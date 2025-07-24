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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.annotation.Nullable;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.Random;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.transport.http.ByteStreamLogging.wrapConnectionInputStream;
import static com.predic8.membrane.core.transport.http.ByteStreamLogging.wrapConnectionOutputStream;
import static com.predic8.membrane.core.util.TextUtil.*;

/**
 * A {@link Connection} is an outbound TCP/IP (with or without TLS) connection, possibly managed
 * by a {@link ConnectionManager}.
 * <p>
 * It is symbiotic to an {@link Exchange} during the exchange's HTTP client
 * call, which starts in {@link HttpClient#call(Exchange)} and ends when the
 * HTTP response body has fully been read (or never, if
 * {@link Request#isBindTargetConnectionToIncoming()} is true).
 * <p>
 * The connection will be registered by the {@link HttpClient} as a
 * {@link MessageObserver} on the {@link Response} to get notified when the HTTP
 * response body has fully been read and it should deassociate itself from the
 * exchange.
 * <p>
 * See {@link ByteStreamLogging} on how to enable traffic logging.
 */
public class Connection implements Closeable, MessageObserver, NonRelevantBodyObserver {

	private static final Logger log = LoggerFactory.getLogger(Connection.class.getName());
	public static final String MEMBRANE_HTTPCLIENT_BUFFER_SIZE = "membrane.httpclient.buffer.size";
	private static volatile ThreadLocal<Random> random = ByteStreamLogging.isLoggingEnabled() ? new ThreadLocal<>() : null;
	private static final int BUFFER_SIZE = getDefaultBufferSize();

	public final ConnectionManager mgr;
	public final String host;
	private final SSLProvider sslProvider;
    private final String sniServerName;
	private final ProxyConfiguration proxyConfiguration;
	private final SSLProvider proxySSLProvider;
    public Socket socket;
	public InputStream in;
	public OutputStream out;

	private long lastUse;
	private long timeout;
	private int maxExchanges = Integer.MAX_VALUE;
	private int completedExchanges;

	private Exchange exchange;
	private boolean keepAttachedToExchange;

	public static Connection open(String host, int port, String localHost, SSLProvider sslProvider, int connectTimeout) throws IOException {
		return open(host, port, localHost, sslProvider, null, connectTimeout);
	}

	public static Connection open(String host, int port, String localHost, SSLProvider sslProvider, ConnectionManager mgr,
								  int connectTimeout, @Nullable String sniServername, @Nullable ProxyConfiguration proxy,
								  @Nullable SSLProvider proxySSLProvider, @Nullable String[] applicationProtocols) throws IOException {
		Connection con = new Connection(mgr, host, sslProvider, sniServername, proxy, proxySSLProvider);

		String origHost = host;
		int origPort = port;
		SSLProvider origSSLProvider = sslProvider;
		String origSniServername = sniServername;
		if (proxy != null) {
			sslProvider = proxySSLProvider;
			host = proxy.getHost();
			port = proxy.getPort();
			sniServername = null;
		}

		if (sslProvider != null) {
			if (isNullOrEmpty(localHost))
				con.socket = sslProvider.createSocket(host, port, connectTimeout, sniServername, applicationProtocols);
			else
				con.socket = sslProvider.createSocket(host, port, InetAddress.getByName(localHost), 0,
						connectTimeout, sniServername, applicationProtocols);
		} else {
			if (isNullOrEmpty(localHost)) {
				con.socket = new Socket();
			} else {
				con.socket = new Socket();
				con.socket.bind(new InetSocketAddress(InetAddress.getByName(localHost), 0));
			}
			con.socket.connect(new InetSocketAddress(host, port), connectTimeout);
		}

		if (proxy != null && origSSLProvider != null) {
			con.doTunnelHandshake(proxy, con.socket, origHost, origPort);
			con.socket = origSSLProvider.createSocket(con.socket, origHost, origPort, connectTimeout, origSniServername, applicationProtocols);
		}

		log.debug("Opened connection on localPort: {}", con.socket.getLocalPort());

		setupStreams(con);
		return con;
	}

	private static void setupStreams(Connection con) throws IOException {
		if (ByteStreamLogging.isLoggingEnabled()) {
			String connectionName = chooseNewConnectionName();
			con.out = new BufferedOutputStream(wrapConnectionOutputStream(con.socket.getOutputStream(), connectionName + " out"), BUFFER_SIZE);
            con.in = new BufferedInputStream(wrapConnectionInputStream(con.socket.getInputStream(), connectionName + " in"), BUFFER_SIZE);
		} else {
			con.out = new BufferedOutputStream(con.socket.getOutputStream(), BUFFER_SIZE);
			con.in = new BufferedInputStream(con.socket.getInputStream(), BUFFER_SIZE);
		}
	}

	private static int getDefaultBufferSize() {
		if (System.getProperty(MEMBRANE_HTTPCLIENT_BUFFER_SIZE) != null)
			return Integer.parseInt(System.getProperty(MEMBRANE_HTTPCLIENT_BUFFER_SIZE));
		return 2048;
	}

	private static String chooseNewConnectionName() {
		Random rand = random.get();
		if (rand == null) {
			rand = new Random();
			random.set(rand);
		}
		return "c" + rand.nextInt();
	}

	public static Connection open(String host, int port, String localHost, SSLProvider sslProvider, ConnectionManager mgr, int connectTimeout) throws IOException {
		return open(host, port, localHost, sslProvider, mgr, connectTimeout, null, null, null, null);
	}

	private Connection(ConnectionManager mgr, String host, @Nullable SSLProvider sslProvider, @Nullable String sniServerName, @Nullable ProxyConfiguration proxy, SSLProvider proxySSLProvider) {
		this.mgr = mgr;
		this.host = host;
		this.sslProvider = sslProvider;
        this.sniServerName = sniServerName;
		this.proxyConfiguration = proxy;
		this.proxySSLProvider = proxySSLProvider;
    }

	public boolean isSame(String host, int port) {
		return socket != null && host.equals(this.host) && port == socket.getPort();
	}

	public void close() throws IOException {
		if (socket == null)
			return;

		log.debug("Closing HTTP connection LocalPort: {}", socket.getLocalPort());

		try {
			if (in != null)
				in.close();

			if (out != null) {
				try {
					out.flush();
					out.close();
				} catch (IOException ignored) {}
			}

			// Test for isClosed() is needed!
			if (!(socket instanceof SSLSocket) && !socket.isClosed())
				socket.shutdownInput();

			socket.close();
			socket = null;

		} finally {
			if (mgr != null)
				mgr.releaseConnection(this); // this.isClosed() == true, but mgr keeps track of number of connections
		}
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
	public void bodyChunk(Chunk chunk) {
		// do nothing
	}

	@Override
	public void bodyChunk(byte[] buffer, int offset, int length) {
		// do nothing
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
		if (socket.getRemoteSocketAddress() != null) {
			return socket.getRemoteSocketAddress().toString();
		}
		return "Not connected to remote!";
	}

    public String getSniServerName() {
        return sniServerName;
    }

	public ProxyConfiguration getProxyConfiguration() {
		return proxyConfiguration;
	}

	public SSLProvider getProxySSLProvider() {
		return proxySSLProvider;
	}

	// From https://docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/samples/sockets/client/SSLSocketClientWithTunneling.java
	/*
	 *
	 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
	 *
	 * Redistribution and use in source and binary forms, with or
	 * without modification, are permitted provided that the following
	 * conditions are met:
	 *
	 * -Redistribution of source code must retain the above copyright
	 * notice, this list of conditions and the following disclaimer.
	 *
	 * Redistribution in binary form must reproduce the above copyright
	 * notice, this list of conditions and the following disclaimer in
	 * the documentation and/or other materials provided with the
	 * distribution.
	 *
	 * Neither the name of Oracle nor the names of
	 * contributors may be used to endorse or promote products derived
	 * from this software without specific prior written permission.
	 *
	 * This software is provided "AS IS," without a warranty of any
	 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
	 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
	 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
	 * EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL
	 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT
	 * OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
	 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR
	 * ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
	 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
	 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
	 * THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
	 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
	 *
	 * You acknowledge that this software is not designed, licensed or
	 * intended for use in the design, construction, operation or
	 * maintenance of any nuclear facility.
	 */
	/*
   * Tell our tunnel where we want to CONNECT, and look for the
   * right reply.  Throw IOException if anything goes wrong.
   */
	private void doTunnelHandshake(ProxyConfiguration proxy, Socket tunnel, String host, int port)
			throws IOException {
		if (log.isDebugEnabled())
			log.debug("send 'CONNECT {}:{}' to {}", host, port, getHostString(proxy));
		OutputStream out = tunnel.getOutputStream();
		out.write(createConnectMessage(proxy, host, port));
		out.flush();

      /*
       * We need to store the reply so we can create a detailed
       * error message to the user.
       */
		byte[] 			reply = new byte[1024];
		int             replyLen = 0;
		int             newlinesSeen = 0;
		boolean         headerDone = false;     /* Done on first newline */

		InputStream     in = tunnel.getInputStream();

		while (newlinesSeen < 2) {
			int i = in.read();
			if (i < 0) {
				throw new IOException("Unexpected EOF from proxy");
			}
			if (i == '\n') {
				headerDone = true;
				++newlinesSeen;
			} else if (i != '\r') {
				newlinesSeen = 0;
				if (!headerDone && replyLen < reply.length) {
					reply[replyLen++] = (byte) i;
				}
			}
		}

      /*
       * Converting the byte array to a string is slightly wasteful
       * in the case where the connection was successful, but it's
       * insignificant compared to the network overhead.
       */
		String replyStr;
		try {
			replyStr = new String(reply, 0, replyLen, "ASCII7");
		} catch (UnsupportedEncodingException ignored) {
			replyStr = new String(reply, 0, replyLen);
		}

        /* Look for '200 OK' response. Probably, some proxies may return HTTP/1.1 back */
		if (!replyStr.startsWith("HTTP/1.0 200") && !replyStr.startsWith("HTTP/1.1 200")) {
			throw new IOException("Unable to tunnel through "
					+ proxy.getHost() + ":" + proxy.getPort()
					+ ".  Proxy returns \"" + replyStr + "\"");
		}

      /* tunneling Handshake was successful! */
	}

	private static @NotNull String getHostString(ProxyConfiguration proxy) {
		return proxy.getHost() + ((proxy.isAuthentication()) ? " authenticated" : "");
	}

	private static byte @NotNull [] createConnectMessage(ProxyConfiguration proxy, String host, int port) {
		String msg = "CONNECT " + host + ":" + port + " HTTP/1.0\r\n"
					 + "User-Agent: " + USERAGENT + "\r\n"
					 + (proxy.isAuthentication() ? ("Proxy-Authorization: " + proxy.getCredentials() + "\r\n") : "")
					 + "\r\n";
		byte[] b;
		try {
          /*
           * We really do want ASCII7 -- the http protocol doesn't change
           * with locale.
           */
			b = msg.getBytes("ASCII7");
		} catch (UnsupportedEncodingException ignored) {
          /*
           * If ASCII7 isn't there, something serious is wrong, but
           * Paranoia Is Good (tm)
           */
			b = msg.getBytes();
		}
		return b;
	}

	public SSLProvider getSslProvider() {
		return sslProvider;
	}
}