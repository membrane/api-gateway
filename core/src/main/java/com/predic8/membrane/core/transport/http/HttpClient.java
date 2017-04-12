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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import javax.annotation.concurrent.GuardedBy;

import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ChunkedBodyTransferrer;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.PlainBodyTransferrer;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.transport.http.client.AuthenticationConfiguration;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.Util;

/**
 * HttpClient with possibly multiple selectable destinations, with internal logic to auto-retry and to
 * switch destinations on failures.
 *
 * Instances are thread-safe.
 */
public class HttpClient {

	private static Logger log = LoggerFactory.getLogger(HttpClient.class.getName());
	@GuardedBy("HttpClient.class")
	private static SSLProvider defaultSSLProvider;

	private final ProxyConfiguration proxy;
	private final SSLContext proxySSLContext;
	private final AuthenticationConfiguration authentication;

	/**
	 * How long to wait between calls to the same destination, in milliseconds.
	 * To prevent hammering one target.
	 * Between calls to different targets (think servers) this waiting time is not applied.
	 *
	 * Note: for reasons of code simplicity, this sleeping time is only applied between direct successive calls
	 * to the same target. If there are multiple targets like one, two, one and it all goes very fast, then
	 * it's possible that the same server gets hit with less time in between.
	 */
	private final int timeBetweenTriesMs = 250;
	/**
	 * See {@link HttpClientConfiguration#setMaxRetries(int)}
	 */
	private final int maxRetries;
	private final int connectTimeout;
	private final String localAddr;

	private final ConnectionManager conMgr;
	private StreamPump.StreamPumpStats streamPumpStats;

	public HttpClient() {
		this(new HttpClientConfiguration());
	}

	public HttpClient(HttpClientConfiguration configuration) {
		proxy = configuration.getProxy();
		if (proxy != null && proxy.getSslParser() != null)
			proxySSLContext = new StaticSSLContext(proxy.getSslParser(), new ResolverMap(), null);
		else
			proxySSLContext = null;
		authentication = configuration.getAuthentication();
		maxRetries = configuration.getMaxRetries();

		connectTimeout = configuration.getConnection().getTimeout();
		localAddr = configuration.getConnection().getLocalAddr();

		conMgr = new ConnectionManager(configuration.getConnection().getKeepAliveTimeout());
	}

	public void setStreamPumpStats(StreamPump.StreamPumpStats streamPumpStats) {
		this.streamPumpStats = streamPumpStats;
	}

	@Override
	protected void finalize() throws Throwable {
		conMgr.shutdownWhenDone();
	}

	private void setRequestURI(Request req, String dest) throws MalformedURLException {
		if (proxy != null || req.isCONNECTRequest())
			req.setUri(dest);
		else {
			if (!dest.startsWith("http"))
				throw new MalformedURLException("The exchange's destination URL ("+dest+") does not start with 'http'. Please specify a <target> within your <serviceProxy>.");
			String originalUri = req.getUri();
			req.setUri(HttpUtil.getPathAndQueryString(dest));
			if("/".equals(originalUri) && req.getUri().isEmpty())
				req.setUri("/");
		}
	}

	private HostColonPort getTargetHostAndPort(boolean connect, String dest) throws MalformedURLException, UnknownHostException {
		if (connect)
			return new HostColonPort(false, dest);

		return new HostColonPort(new URL(dest));
	}

	private HostColonPort init(Exchange exc, String dest, boolean adjustHostHeader) throws UnknownHostException, IOException, MalformedURLException {
		setRequestURI(exc.getRequest(), dest);
		HostColonPort target = getTargetHostAndPort(exc.getRequest().isCONNECTRequest(), dest);

		if (authentication != null)
			exc.getRequest().getHeader().setAuthorization(authentication.getUsername(), authentication.getPassword());

		if (adjustHostHeader && (exc.getRule() == null || exc.getRule().isTargetAdjustHostHeader())) {
			URL d = new URL(dest);
			exc.getRequest().getHeader().setHost(d.getHost() + ":" + HttpUtil.getPort(d));
		}
		return target;
	}

	private SSLProvider getOutboundSSLProvider(Exchange exc, HostColonPort hcp) {
		Object sslPropObj = exc.getProperty(Exchange.SSL_CONTEXT);
		if(sslPropObj != null)
			return (SSLProvider) sslPropObj;
		if (hcp.useSSL)
			return getDefaultSSLProvider();
		return null;
	}

	private static synchronized SSLProvider getDefaultSSLProvider() {
		if (defaultSSLProvider == null)
			defaultSSLProvider = new StaticSSLContext(new SSLParser(), null, null);
		return defaultSSLProvider;
	}

	public Exchange call(Exchange exc) throws Exception {
		return call(exc, true, true);
	}

	public Exchange call(Exchange exc, boolean adjustHostHeader, boolean failOverOn5XX) throws Exception {
		if (exc.getDestinations().isEmpty())
			throw new IllegalStateException("List of destinations is empty. Please specify at least one destination.");

		int counter = 0;
		Exception exception = null;
		Object trackNodeStatusObj = exc.getProperty(Exchange.TRACK_NODE_STATUS);
		boolean trackNodeStatus = trackNodeStatusObj != null && trackNodeStatusObj instanceof Boolean && (Boolean)trackNodeStatusObj;
		while (counter < maxRetries) {
			Connection con = null;
			String dest = getDestination(exc, counter);
			HostColonPort target = null;
			try {
				log.debug("try # " + counter + " to " + dest);
				target = init(exc, dest, adjustHostHeader);
				if (counter == 0) {
					con = exc.getTargetConnection();
					if (con != null) {
						if (!con.isSame(target.host, target.port)) {
							con.close();
							con = null;
						} else {
							con.setKeepAttachedToExchange(true);
						}
					}
				}
				SSLProvider sslProvider = getOutboundSSLProvider(exc, target);
				if (con == null) {
					con = conMgr.getConnection(target.host, target.port, localAddr, sslProvider, connectTimeout, getSNIServerName(exc), proxy, proxySSLContext);
					con.setKeepAttachedToExchange(exc.getRequest().isBindTargetConnectionToIncoming());
					exc.setTargetConnection(con);
				}
				if (proxy != null && sslProvider == null)
					// if we use a proxy for a plain HTTP (=non-HTTPS) request, attach the proxy credentials.
					exc.getRequest().getHeader().setProxyAutorization(proxy.getCredentials());
				Response response;
				String newProtocol = null;

				if (exc.getRequest().isCONNECTRequest()) {
					handleConnectRequest(exc, con);
					response = Response.ok().build();
					newProtocol = "CONNECT";
				} else {
					response = doCall(exc, con);
					if (trackNodeStatus)
						exc.setNodeStatusCode(counter, response.getStatusCode());

					if (exc.getProperty(Exchange.ALLOW_WEBSOCKET) == Boolean.TRUE && isUpgradeToResponse(response, "websocket")) {
						log.debug("Upgrading to WebSocket protocol.");
						newProtocol = "WebSocket";
					}
					if (exc.getProperty(Exchange.ALLOW_TCP) == Boolean.TRUE && isUpgradeToResponse(response, "tcp")) {
						log.debug("Upgrading to TCP protocol.");
						newProtocol = "TCP";
					}
					if (exc.getProperty(Exchange.ALLOW_SPDY) == Boolean.TRUE && isUpgradeToResponse(response, "SPDY/3.1")) {
						log.debug("Upgrading to SPDY/3.1 protocol.");
						newProtocol = "SPDY/3.1";
					}
				}

				if (newProtocol != null) {
					setupConnectionForwarding(exc, con, newProtocol, streamPumpStats);
					exc.getDestinations().clear();
					exc.getDestinations().add(dest);
					con.setExchange(exc);
					exc.setResponse(response);
					return exc;
				}

				boolean is5XX = 500 <= response.getStatusCode() && response.getStatusCode() < 600;
				if (!failOverOn5XX || !is5XX || counter == maxRetries-1) {
					applyKeepAliveHeader(response, con);
					exc.getDestinations().clear();
					exc.getDestinations().add(dest);
					con.setExchange(exc);
					response.addObserver(con);
					exc.setResponse(response);
					return exc;
				}
				// java.net.SocketException: Software caused connection abort: socket write error
			} catch (ConnectException e) {
				exception = e;
				log.info("Connection to " + (target == null ? dest : target ) + " refused.");
			} catch(SocketException e){
				if ( e.getMessage().contains("Software caused connection abort")) {
					log.info("Connection to " + dest + " was aborted externally. Maybe by the server or the OS Membrane is running on.");
				} else if (e.getMessage().contains("Connection reset") ) {
					log.info("Connection to " + dest + " was reset externally. Maybe by the server or the OS Membrane is running on.");
				} else {
					logException(exc, counter, e);
				}
				exception = e;
			} catch (UnknownHostException e) {
				log.warn("Unknown host: " + (target == null ? dest : target ));
				exception = e;
				if (exc.getDestinations().size() < 2) {
					//don't retry this host, it's useless. (it's very unlikely that it will work after timeBetweenTriesMs)
					break;
				}
			} catch (EOFWhileReadingFirstLineException e) {
				log.debug("Server connection to " + dest + " terminated before line was read. Line so far: " + e.getLineSoFar());
				exception = e;
			} catch (NoResponseException e) {
				throw e;
			} catch (Exception e) {
				logException(exc, counter, e);
				exception = e;
			}
			finally	{
				if (trackNodeStatus) {
					if(exception != null){
						exc.setNodeException(counter, exception);
					}
				}
			}
			counter++;
			if (exc.getDestinations().size() == 1) {
				//as documented above, the sleep timeout is only applied between successive calls to the same destination.
				Thread.sleep(timeBetweenTriesMs);
			}
		}
		throw exception;
	}

	private String getSNIServerName(Exchange exc) {
		Object sniObject = exc.getProperty(Exchange.SNI_SERVER_NAME);
		if(sniObject == null)
			return null;
		return (String) sniObject;
	}

	private void applyKeepAliveHeader(Response response, Connection con) {
		String value = response.getHeader().getFirstValue(Header.KEEP_ALIVE);
		if (value == null)
			return;

		long timeoutSeconds = Header.parseKeepAliveHeader(value, Header.TIMEOUT);
		if (timeoutSeconds != -1)
			con.setTimeout(timeoutSeconds * 1000);

		long max = Header.parseKeepAliveHeader(value, Header.MAX);
		if (max != -1 && max < con.getMaxExchanges())
			con.setMaxExchanges((int)max);
	}

	/**
	 * Returns the target destination to use for this attempt.
	 * @param counter starting at 0 meaning the first.
	 */
	private String getDestination(Exchange exc, int counter) {
		return exc.getDestinations().get(counter % exc.getDestinations().size());
	}

	private void logException(Exchange exc, int counter, Exception e) throws IOException {
		if (log.isDebugEnabled()) {
			StringBuilder msg = new StringBuilder();
			msg.append("try # ");
			msg.append(counter);
			msg.append(" failed\n");

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			exc.getRequest().writeStartLine(baos);
			exc.getRequest().getHeader().write(baos);
			msg.append(Constants.ISO_8859_1_CHARSET.decode(ByteBuffer.wrap(baos.toByteArray())));

			if (e != null)
				log.debug("{}",msg, e);
			else
				log.debug("{}",msg);
		}
	}

	private Response doCall(Exchange exc, Connection con) throws IOException, EndOfStreamException {
		exc.getRequest().write(con.out);
		exc.setTimeReqSent(System.currentTimeMillis());

		if (exc.getRequest().isHTTP10()) {
			shutDownRequestInputOutput(exc, con);
		}

		Response res = new Response();
		res.read(con.in, !exc.getRequest().isHEADRequest());

		if (res.getStatusCode() == 100) {
			do100ExpectedHandling(exc, res, con);
		}

		exc.setReceived();
		exc.setTimeResReceived(System.currentTimeMillis());
		return res;
	}

	public static void setupConnectionForwarding(Exchange exc, final Connection con, final String protocol, StreamPump.StreamPumpStats streamPumpStats) throws SocketException {
		final HttpServerHandler hsr = (HttpServerHandler)exc.getHandler();
		String source = hsr.getSourceSocket().getRemoteSocketAddress().toString();
		String dest = con.toString();
		final StreamPump a;
		final StreamPump b;
		if("WebSocket".equals(protocol)){
			a = new WebSocketStreamPump(hsr.getSrcIn(), con.out, streamPumpStats, protocol + " " + source + " -> " + dest, exc.getRule(),true);
			b = new WebSocketStreamPump(con.in, hsr.getSrcOut(), streamPumpStats, protocol + " " + source + " <- " + dest, exc.getRule(),false);
		}
		else {
			a = new StreamPump(hsr.getSrcIn(), con.out, streamPumpStats, protocol + " " + source + " -> " + dest, exc.getRule());
			b = new StreamPump(con.in, hsr.getSrcOut(), streamPumpStats, protocol + " " + source + " <- " + dest, exc.getRule());
		}

		hsr.getSourceSocket().setSoTimeout(0);

		exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {

			@Override
			public void setExchangeFinished() {
				String threadName = Thread.currentThread().getName();
				new Thread(b, threadName + " " + protocol + " Backward Thread").start();
				try {
					Thread.currentThread().setName(threadName + " " + protocol + " Onward Thread");
					a.run();
				} finally {
					try {
						con.close();
					} catch (IOException e) {
						log.debug("", e);
					}
				}
			}
		});
	}

	private boolean isUpgradeToResponse(Response res, String protocol) {
		return res.getStatusCode() == 101 &&
				"upgrade".equalsIgnoreCase(res.getHeader().getFirstValue(Header.CONNECTION)) &&
				protocol.equalsIgnoreCase(res.getHeader().getFirstValue(Header.UPGRADE));
	}

	private void handleConnectRequest(Exchange exc, Connection con) throws IOException, EndOfStreamException {
		if (proxy != null) {
			exc.getRequest().write(con.out);
			Response response = new Response();
			response.read(con.in, false);
			log.debug("Status code response on CONNECT request: " + response.getStatusCode());
		}
		exc.getRequest().setUri(Constants.N_A);
	}

	private void do100ExpectedHandling(Exchange exc, Response response, Connection con) throws IOException, EndOfStreamException {
		exc.getRequest().getBody().write(exc.getRequest().getHeader().isChunked() ? new ChunkedBodyTransferrer(con.out) : new PlainBodyTransferrer(con.out));
		con.out.flush();
		response.read(con.in, !exc.getRequest().isHEADRequest());
	}

	private void shutDownRequestInputOutput(Exchange exc, Connection con) throws IOException {
		exc.getHandler().shutdownInput();
		Util.shutdownOutput(con.socket);
	}

	ConnectionManager getConnectionManager() {
		return conMgr;
	}
}
