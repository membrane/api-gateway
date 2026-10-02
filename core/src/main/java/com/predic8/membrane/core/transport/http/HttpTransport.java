/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.proxies.SSLableProxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static com.google.common.base.Objects.equal;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @description <p>Opens the listening ports of all APIs, accepts client connections and runs every exchange
 * through the global flow. Each connection is served by its own thread from a shared pool.</p>
 * <p>Declaring a transport under <code>components</code> overrides the default settings for inbound HTTP
 * connections, such as timeouts, thread pool sizes and TCP options. A configuration may declare at most one
 * transport; without one, Membrane uses a transport with default settings.</p>
 * @yaml <pre><code>
 * components:
 *   inbound:
 *     transport:
 *       socketTimeout: 60000
 *       maxThreadPoolSize: 300
 * ---
 * api:
 *   port: 2000
 *   target:
 *     url: https://api.predic8.de
 * </code></pre>
 */
@MCElement(name="transport")
public class HttpTransport extends Transport {

	private static final Logger log = LoggerFactory.getLogger(HttpTransport.class.getName());

	private int socketTimeout = 30000;
	private int forceSocketCloseOnHotDeployAfter = 30000;
	private boolean tcpNoDelay = true;
	private int backlog = 50;

	private final Map<Integer, Map<IpPort, HttpEndpointListener>> portListenerMapping = new HashMap<>();
	private final List<WeakReference<HttpEndpointListener>> stillRunning = new ArrayList<>();

	private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(20,
			MAX_VALUE, 60L, SECONDS,
			new SynchronousQueue<>(), new HttpServerThreadFactory());

	@Override
	public void init(Router router) {
		super.init(router);
	}

	/**
	 * Closes the corresponding server port. Note that connections might still be open and exchanges still running after
	 * this method completes.
	 */
	public synchronized void closePort(IpPort p) {
	    Map<IpPort, HttpEndpointListener> mih = portListenerMapping.get(p.port());
	    if (mih == null || mih.isEmpty()) {
	        return;
	    }
		HttpEndpointListener plt = mih.get(p);
		if (plt == null)
			return;
		log.info("Closing server port: {}", p);

		try {
			plt.closePort();
			plt.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
            log.error("Error closing server port: {}", p.port());
        }
        mih.remove(p);
		if (mih.isEmpty()) {
		    portListenerMapping.remove(p.port());
		}
		stillRunning.add(new WeakReference<>(plt));
	}

	@Override
	public synchronized void closeAll(boolean waitForCompletion) {
		log.debug("Closing all network server sockets.");
		List<IpPort> all = new ArrayList<>();
		for (Map<IpPort, HttpEndpointListener> v : portListenerMapping.values()) {
		    all.addAll(v.keySet());
		}
		for (IpPort ipPort : all) { // don't iterate thru portListenerMapping !!!
			closePort(ipPort);
		}
		log.debug("Closing all stream pumps.");
		Router router = getRouter();
		if (router != null)
			router.getStatistics().getStreamPumpStats().closeAllStreamPumps();

		if (waitForCompletion) {
			long now = System.currentTimeMillis();
			log.debug("Waiting for running exchanges to finish.");
			executorService.shutdown();
			try {
				while (true) {
                    closeConnections(closeOnlyIdleConnections(now));
					if (executorService.awaitTermination(5, SECONDS))
						break;
					log.warn("Still waiting for running exchanges to finish. (Set <transport forceSocketCloseOnHotDeployAfter=\"{}\"> to a lower value to forcibly close connections more quickly.",forceSocketCloseOnHotDeployAfter);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/*
	 * Close all connections after some time
	 */
	private boolean closeOnlyIdleConnections(long now) {
		return System.currentTimeMillis() - now <= forceSocketCloseOnHotDeployAfter;
	}

	private void closeConnections(boolean onlyIdle) {
		ArrayList<WeakReference<HttpEndpointListener>> remove = new ArrayList<>();
		for (WeakReference<HttpEndpointListener> whel : stillRunning) {
			HttpEndpointListener hel = whel.get();
			if (hel == null)
				remove.add(whel);
			else
				if (hel.closeConnections(onlyIdle))
					remove.add(whel);
		}
		for (WeakReference<HttpEndpointListener> whel : remove)
			stillRunning.remove(whel);
	}

	/**
	 * @param port Port to open
	 * @throws IOException If port can not be opened
	 */
	@Override
	public synchronized void openPort(String ip, int port, SSLProvider sslProvider) throws IOException {
	    if (port == -1)
			throw new RuntimeException("The port-attribute is missing (probably on a <serviceProxy> element).");

		Map<IpPort, HttpEndpointListener> mih = portListenerMapping.computeIfAbsent(port, k -> new HashMap<>());
		IpPort p = new IpPort(ip, port);
	    HttpEndpointListener hel = mih.get(p);
	    if (hel != null) { // already listen on the same "ip:port"
	        if (equal(sslProvider, hel.getSslProvider())) {
	            return; // O.K. both use the equivalent ssl provider
	        }
	        throw new RuntimeException(format("Lister thread on %s should use the same SSL config", p.toShortString()));
	    }
	    if ((ip == null && !mih.isEmpty())                             // '*:port' vs 'XXX:port'
	      || (ip != null && mih.containsKey(new IpPort((String) null, port)))   // 'XXX:port' vs '*:port'
	      ) {
	        throw new RuntimeException(createDiffInterfacesErrorMsg(p,mih));
	    }

		HttpEndpointListener portListenerThread = new HttpEndpointListener(p, this, sslProvider);
		mih.put(p, portListenerThread);
		portListenerThread.start();
	}

	@Override
	public void openPort(SSLableProxy proxy) throws IOException {
		openPort(proxy.getKey().getIp(), proxy.getKey().getPort(), proxy.getSslInboundContext());
	}

	@Override
	public String getOpenBackendConnections(int port) {
		Map<IpPort, HttpEndpointListener> pl = portListenerMapping.get(port);
		if (pl == null) return "N/A";

		return pl.entrySet().stream()
				.filter(e -> e.getKey().port() == port)
				.findFirst()
				.map(e -> Integer.toString(e.getValue().getNumberOfOpenConnections()))
				.orElseThrow();
	}

	private static String createDiffInterfacesErrorMsg(IpPort p, Map<IpPort, HttpEndpointListener> mih) {
	    final StringBuilder sb = new StringBuilder("Conflict with listening on the same net interfaces [")
	        .append(p.toShortString()).append(", ");
	    for (IpPort ip : mih.keySet()) {
	        sb.append(ip.toShortString()).append(", ");
	    }
		return sb.replace(sb.length() - 2, sb.length(), "]").toString();
	}

	public int getCoreThreadPoolSize() {
		return executorService.getCorePoolSize();
	}

	/**
	 * @description Number of threads the pool keeps alive while idle. Threads are created on demand; beyond this
	 * number, idle threads are released after 60 seconds.
	 * @default 20
	 * @example 5
	 */
	@MCAttribute
	public void setCoreThreadPoolSize(int corePoolSize) {
		executorService.setCorePoolSize(corePoolSize);
	}

	public int getMaxThreadPoolSize() {
		return executorService.getMaximumPoolSize();
	}

	/**
	 * @description Maximum number of threads, and therefore of concurrent client connections, since each connection
	 * occupies one thread. A connection accepted while all threads are busy is closed immediately.
	 * @default unlimited
	 * @example 300
	 */
	@MCAttribute
	public void setMaxThreadPoolSize(int value) {
		executorService.setMaximumPoolSize(value);
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public int getSocketTimeout() {
		return socketTimeout;
	}

	/**
	 * @description Read timeout for client connections in milliseconds. A connection that sends no data for this
	 * long, e.g. an idle keep-alive connection, is closed. <code>0</code> disables the timeout.
	 * @default 30000
	 * @example 60000
	 */
	@MCAttribute
	public void setSocketTimeout(int timeout) {
		this.socketTimeout = timeout;
	}

	public boolean isTcpNoDelay() {
		return tcpNoDelay;
	}

	/**
	 * @description Sets <code>TCP_NODELAY</code> on client connections, which sends data immediately instead of
	 * buffering small writes into larger packets (Nagle's algorithm). Lowers latency at the cost of more packets.
	 * @default true
	 * @example false
	 */
	@MCAttribute
	public void setTcpNoDelay(boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
	}

	@Override
	public boolean isOpeningPorts() {
		return true;
	}

	public int getForceSocketCloseOnHotDeployAfter() {
		return forceSocketCloseOnHotDeployAfter;
	}

	/**
	 * @description Grace period in milliseconds when Membrane stops or reloads its configuration (hot deployment).
	 * During this period only idle connections are closed, so running exchanges can finish; afterwards all remaining
	 * connections are closed.
	 * @default 30000
	 * @example 5000
	 */
	@MCAttribute
	public void setForceSocketCloseOnHotDeployAfter(int forceSocketCloseOnHotDeployAfter) {
		this.forceSocketCloseOnHotDeployAfter = forceSocketCloseOnHotDeployAfter;
	}

	public int getBacklog() {
		return backlog;
	}

	/**
	 * @description Maximum number of incoming connections the operating system queues on a listening port before
	 * Membrane accepts them. Beyond that, the operating system refuses or drops new connection attempts.
	 * @default 50
	 * @example 200
	 */
	@MCAttribute
	public void setBacklog(int backlog) {
		this.backlog = backlog;
	}
}
