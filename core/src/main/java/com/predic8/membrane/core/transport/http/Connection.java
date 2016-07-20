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

import static com.predic8.membrane.core.util.TextUtil.isNullOrEmpty;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.MessageObserver;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

/**
 * A {@link Connection} is an outbound TCP/IP connection, possibly managed
 * by a {@link ConnectionManager}.
 *
 * It is symbiotic to an {@link Exchange} during the exchange's HTTP client
 * call, which starts in {@link HttpClient#call(Exchange)} and ends when the
 * HTTP response body has fully been read (or never, if
 * {@link Request#isBindTargetConnectionToIncoming()} is true).
 *
 * The connection will be registered by the {@link HttpClient} as a
 * {@link MessageObserver} on the {@link Response} to get notified when the HTTP
 * response body has fully been read and it should deassociate itself from the
 * exchange.
 */
public class Connection implements MessageObserver {

	private static Logger log = LoggerFactory.getLogger(Connection.class.getName());

	public final ConnectionManager mgr;
	public final String host;
	public Socket socket;
	public InputStream in;
	public OutputStream out;
    private String sniServerName;

	private long lastUse;
	private long timeout;
	private int maxExchanges = Integer.MAX_VALUE;
	private int completedExchanges;

	private Exchange exchange;
	private boolean keepAttachedToExchange;

	public static Connection open(String host, int port, String localHost, SSLProvider sslProvider, int connectTimeout) throws UnknownHostException, IOException {
		return open(host, port, localHost, sslProvider, null, connectTimeout);
	}

	public static Connection open(String host, int port, String localHost, SSLProvider sslProvider, ConnectionManager mgr, int connectTimeout, @Nullable String sniServername) throws UnknownHostException, IOException {
		Connection con = new Connection(mgr, host,sniServername);

		if (sslProvider != null) {
			if (isNullOrEmpty(localHost))
				con.socket = sslProvider.createSocket(host, port, connectTimeout,sniServername);
			else
				con.socket = sslProvider.createSocket(host, port, InetAddress.getByName(localHost), 0, connectTimeout,sniServername);
		} else {
			if (isNullOrEmpty(localHost)) {
				con.socket = new Socket();
			} else {
				con.socket = new Socket();
				con.socket.bind(new InetSocketAddress(InetAddress.getByName(localHost), 0));
			}
			con.socket.connect(new InetSocketAddress(host, port), connectTimeout);
		}

		log.debug("Opened connection on localPort: " + con.socket.getLocalPort());
		//Creating output stream before input stream is suggested.
		con.out = new BufferedOutputStream(con.socket.getOutputStream(), 2048);
		con.in = new BufferedInputStream(con.socket.getInputStream(), 2048);

		return con;
	}

	public static Connection open(String host, int port, String localHost, SSLProvider sslProvider, ConnectionManager mgr, int connectTimeout) throws UnknownHostException, IOException {
		return open(host,port,localHost,sslProvider,mgr,connectTimeout,null);
	}

	private Connection(ConnectionManager mgr, String host, @Nullable String sniServerName) {
		this.mgr = mgr;
		this.host = host;
        this.sniServerName = sniServerName;
	}

	public boolean isSame(String host, int port) {
		return socket != null && host.equals(this.host) && port == socket.getPort();
	}

	public void close() throws IOException {
		if (socket == null)
			return;

		log.debug("Closing HTTP connection LocalPort: " + socket.getLocalPort());

		if (in != null)
			in.close();

		if (out != null) {
			out.flush();
			out.close();
		}

		// Test for isClosed() is needed!
		if (!(socket instanceof SSLSocket) && !socket.isClosed())
			socket.shutdownInput();

		socket.close();
		socket = null;

		if (mgr != null)
			mgr.releaseConnection(this); // this.isClosed() == true, but mgr keeps track of number of connections
	}

	@Override
	protected void finalize() throws Throwable {
		close();
	}

	public boolean isClosed() {
		return socket == null || socket.isClosed();
	}

	/**
	 * See {@link ConnectionManager} for documentation.
	 */
	public void release() throws IOException {
		if (mgr != null)
			mgr.releaseConnection(this);
		else
			close();
	}

	@Override
	public void bodyRequested(AbstractBody body) {
		// do nothing
	}

	@Override
	public void bodyComplete(AbstractBody body) {
		lastUse = System.currentTimeMillis();
		completedExchanges++;

		try {
			if (exchange != null) {
				if (exchange.canKeepConnectionAlive()) {
					if (keepAttachedToExchange)
						return;
					else
						release();
				} else {
					close();
				}
				exchange.setTargetConnection(null);
				exchange = null;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}




	public final void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public final long getTimeout() {
		return timeout;
	}

	public final int getMaxExchanges() {
		return maxExchanges;
	}

	public final void setMaxExchanges(int maxExchanges) {
		this.maxExchanges = maxExchanges;
	}

	public final int getCompletedExchanges() {
		return completedExchanges;
	}

	public final long getLastUse() {
		return lastUse;
	}

	public String getHost() {
		return host;
	}

	void setKeepAttachedToExchange(boolean keepAttachedToExchange) {
		this.keepAttachedToExchange = keepAttachedToExchange;
	}

	void setExchange(Exchange exchange) {
		this.exchange = exchange;
	}

	@Override
	public String toString() {
		return socket.getRemoteSocketAddress().toString();
	}

    public String getSniServerName() {
        return sniServerName;
    }

    public void setSniServerName(String sniServerName) {
        this.sniServerName = sniServerName;
    }
}