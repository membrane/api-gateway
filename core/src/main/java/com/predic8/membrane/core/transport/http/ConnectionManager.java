/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.util.TimerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.transport.ssl.SSLProvider;

import javax.annotation.Nullable;

/**
 * Pools TCP/IP connections, holding them open for a configurable number of milliseconds.
 *
 * With keep-alive use as follows:
 * <code>
 * Connection connection = connectionManager.getConnection(...);
 * try {
 *   ...
 * } finally {
 *   connection.release();
 * }
 * </code>
 *
 * Without keep-alive replace {@link Connection#release()} by {@link Connection#close()}.
 *
 * Note that you should call {@link Connection#release()} exactly once, or alternatively
 * {@link Connection#close()} at least once.
 */
public class ConnectionManager {

	private static Logger log = LoggerFactory.getLogger(ConnectionManager.class.getName());

	private final long keepAliveTimeout;
	private final long autoCloseInterval;
	private final AtomicInteger numberInPool = new AtomicInteger();
	private final HashMap<ConnectionKey, ArrayList<OldConnection>> availableConnections =
            new HashMap<>(); // guarded by this
	private volatile boolean shutdownWhenDone = false;
	private TimerManager selfCreatedTimerManager;

	private static class OldConnection {
		public final Connection connection;
		public final long deathTime;

		public OldConnection(Connection connection, long defaultKeepAliveTimeout) {
			this.connection = connection;
			long lastUse = connection.getLastUse();
			if (lastUse == 0)
				lastUse = System.currentTimeMillis();
			long delta = connection.getTimeout();
			if (delta == 0)
				delta = defaultKeepAliveTimeout;
			if (delta > 400)
				delta -= 400; // slippage
			else
				delta = 0;
			if (connection.getCompletedExchanges() >= connection.getMaxExchanges())
				delta = 0; // let the background closer do its job
			this.deathTime = lastUse + delta;
		}
	}

	/**
	 * @param keepAliveTimeout milliseconds to keep idle connections around for
	 * @param timerManager a TimerManager instance to use to close idle connections. Providing a TimerManager instance
	 *                     prevents the ConnectionManager from creating its own Timer (and TimerManager). Can be null.
	 */
	public ConnectionManager(long keepAliveTimeout, @Nullable TimerManager timerManager) {
		this.keepAliveTimeout = keepAliveTimeout;
		this.autoCloseInterval = keepAliveTimeout * 2;
		if (timerManager == null) {
			selfCreatedTimerManager = timerManager = new TimerManager();
		}

		timerManager.schedulePeriodicTask(new TimerTask() {
			@Override
			public void run() {
				if (closeOldConnections() == 0 && shutdownWhenDone) {
					cancel();
					if (selfCreatedTimerManager != null)
						selfCreatedTimerManager.shutdown();
				}
			}
		}, autoCloseInterval, "Connection Closer");
	}

	public Connection getConnection(String host, int port, String localHost, SSLProvider sslProvider, int connectTimeout, @Nullable String sniServerName,
		@Nullable ProxyConfiguration proxy, @Nullable SSLContext proxySSLContext, @Nullable String[] applicationProtocols) throws UnknownHostException, IOException {

		log.debug("connection requested for " + host + ":" + port + (proxy != null ? " via " + proxy.getHost() + ":" + proxy.getPort() : ""));

		log.debug("Number of connections in pool: " + numberInPool.get());

		String cacheHost = host;
		int cachePort = port;
		if (proxy != null && sslProvider == null) {
			// if a proxy is used, but no SSLProvider, the host:port do not have to be included in the ConnectionKey,
			// as this simply caches the connection to the proxy
			cacheHost = "";
			cachePort = 0;
		}

		ConnectionKey key = new ConnectionKey(cacheHost, cachePort, sslProvider, sniServerName, proxy, proxySSLContext, applicationProtocols);
		long now = System.currentTimeMillis();

		synchronized(this) {
			ArrayList<OldConnection> l = availableConnections.get(key);
			if (l != null) {
				int i = l.size() - 1;
				while(i >= 0) {
					OldConnection c = l.get(i);
					if (c.deathTime > now) {
						l.remove(i);
						return c.connection;
					}
					Collections.swap(l, 0, i);
					i--;
				}
			}
		}

		Connection result = Connection.open(host, port, localHost, sslProvider, this, connectTimeout,
				sniServerName, proxy, proxySSLContext, applicationProtocols);
		numberInPool.incrementAndGet();
		return result;
	}

	public Connection getConnection(String host, int port, String localHost, SSLProvider sslProvider, int connectTimeout) throws UnknownHostException, IOException {
		return getConnection(host,port,localHost,sslProvider,connectTimeout,null,null,null,null);
	}

	public void releaseConnection(Connection connection) {
		if (connection == null)
			return;

		if (connection.isClosed()) {
			numberInPool.decrementAndGet();
			return;
		}

		ConnectionKey key = new ConnectionKey(connection.getHost(), connection.socket.getPort(),
				connection.getSslProvider(), connection.getSniServerName(), connection.getProxyConfiguration(),
				connection.getProxySSLProvider(), connection.getApplicationProtocols());
		OldConnection o = new OldConnection(connection, keepAliveTimeout);
		ArrayList<OldConnection> l;
		synchronized(this) {
			l = availableConnections.get(key);
			if (l == null) {
				l = new ArrayList<>();
				availableConnections.put(key, l);
			}
			l.add(o);
		}
	}

	private int closeOldConnections() {
		ArrayList<ConnectionKey> toRemove = new ArrayList<>();
		ArrayList<Connection> toClose = new ArrayList<>();
		long now = System.currentTimeMillis();
		log.trace("closing old connections");
		int closed = 0, remaining;
		synchronized(this) {
			// close connections after their timeout
			for (Map.Entry<ConnectionKey, ArrayList<OldConnection>> e : availableConnections.entrySet()) {
				ArrayList<OldConnection> l = e.getValue();
				for (int i = 0; i < l.size(); i++) {
					OldConnection o = l.get(i);
					if (o.deathTime < now) {
						// replace [i] by [last]
						if (i == l.size() - 1)
							l.remove(i);
						else
							l.set(i, l.remove(l.size() - 1));
						--i;
						closed++;
						toClose.add(o.connection);
					}
				}
				if (l.isEmpty())
					toRemove.add(e.getKey());
			}
			for (ConnectionKey remove : toRemove)
				availableConnections.remove(remove);
			remaining = availableConnections.size();
		}
		for (Connection c : toClose) {
			try {
				c.close();
			} catch (Exception e) {
				// do nothing
			}
		}
		if (closed != 0)
			log.debug("closed " + closed + " connections");
		return remaining;
	}

	public void shutdownWhenDone() {
		shutdownWhenDone = true;
	}

	public int getNumberInPool() {
		return numberInPool.get();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Number in pool: " + numberInPool.get() + "\n");
		synchronized(this) {
			for (Map.Entry<ConnectionKey, ArrayList<OldConnection>> e : availableConnections.entrySet()) {
				sb.append("To " + e.getKey() + ": " + e.getValue().size() + "\n");
			}
		}
		return sb.toString();
	}
}
