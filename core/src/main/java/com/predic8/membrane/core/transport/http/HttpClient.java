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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http2.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import javax.annotation.*;
import javax.annotation.concurrent.*;
import java.io.*;
import java.net.*;
import java.nio.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static java.lang.Boolean.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * HttpClient with possibly multiple selectable destinations, with internal logic to auto-retry and to
 * switch destinations on failures.
 *
 * Instances are thread-safe.
 */
public class HttpClient implements AutoCloseable {
	public static final String HTTP2 = "h2";

	private static final Logger log = LoggerFactory.getLogger(HttpClient.class.getName());

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
	private final SSLContext sslContext;
	private final boolean useHttp2;

	private final ConnectionManager conMgr;
	private final Http2ClientPool http2ClientPool;
	private StreamPump.StreamPumpStats streamPumpStats;

	/**
	 * TODO make injectable, make it an optional feature, don't pay for what you don't use.
	 */
//	private HttpClientStatusEventBus httpClientStatusEventBus = HttpClientStatusEventBus.getService();

	private static final String[] HTTP2_PROTOCOLS = new String[] { "h2" };

	public HttpClient() {
		this(null, null);
	}

	public HttpClient(@Nullable HttpClientConfiguration configuration) {
		this(configuration, null);
	}

	public HttpClient(@Nullable HttpClientConfiguration configuration, @Nullable TimerManager timerManager) {
		if (configuration == null)
			configuration = new HttpClientConfiguration();
		proxy = configuration.getProxy();
		if (proxy != null && proxy.getSslParser() != null)
			proxySSLContext = new StaticSSLContext(proxy.getSslParser(), new ResolverMap(), null);
		else
			proxySSLContext = null;
		if (configuration.getSslParser() != null) {
			if (configuration.getBaseLocation() == null)
				throw new RuntimeException("Cannot find keystores as base location is unknown");
			sslContext = new StaticSSLContext(configuration.getSslParser(), new ResolverMap(), configuration.getBaseLocation());
		}else
			sslContext = null;
		authentication = configuration.getAuthentication();
		maxRetries = configuration.getMaxRetries();

		connectTimeout = configuration.getConnection().getTimeout();
		localAddr = configuration.getConnection().getLocalAddr();

		conMgr = new ConnectionManager(configuration.getConnection().getKeepAliveTimeout(), timerManager);

		useHttp2 = configuration.isUseExperimentalHttp2();
		if (useHttp2)
			http2ClientPool = new Http2ClientPool(configuration.getConnection().getKeepAliveTimeout());
		else
			http2ClientPool = null;
	}

	public void setStreamPumpStats(StreamPump.StreamPumpStats streamPumpStats) {
		this.streamPumpStats = streamPumpStats;
	}

	@Override
	protected void finalize() throws Throwable {
		close();
	}

    private void setRequestURI(Request req, String dest) throws MalformedURLException {
        if (proxy != null || req.isCONNECTRequest()) {
            req.setUri(dest);
            return;
        }

        if (!dest.startsWith("http"))
            throw new MalformedURLException("The exchange's destination URL (" + dest + ") does not start with 'http'. Please specify a <target> within your <serviceProxy>.");
        String originalUri = req.getUri();
        try {
            req.setUri(HttpUtil.getPathAndQueryString(dest));
        } catch (MalformedURLException e) {
            throw new RuntimeException("while handling destination '" + dest + "'", e);
        }

        // Make sure if the request had no path and the destination has also no path
        // to continure with no path. Maybe for STOMP?
        if ("/".equals(originalUri) && req.getUri().isEmpty())
            req.setUri("/");

    }

	private HostColonPort getTargetHostAndPort(boolean connect, String dest) throws MalformedURLException {
		if (connect)
			return new HostColonPort(false, dest);

		return new HostColonPort(new URL(dest));
	}

	private HostColonPort init(Exchange exc, String dest, boolean adjustHostHeader) throws IOException {
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
		Object sslPropObj = exc.getProperty(SSL_CONTEXT);
		if(sslPropObj != null)
			return (SSLProvider) sslPropObj;
		if(hcp.useSSL)
			if(sslContext != null)
				return sslContext;
			else
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

		HttpClientStatusEventBus httpClientStatusEventBus = (HttpClientStatusEventBus) exc.getProperty(HttpClientStatusEventBus.EXCHANGE_PROPERTY_NAME);

		int counter = 0;
		Exception exception = null;
		Object trackNodeStatusObj = exc.getProperty(TRACK_NODE_STATUS);
		boolean trackNodeStatus = trackNodeStatusObj instanceof Boolean && (Boolean) trackNodeStatusObj;
		while (counter < maxRetries) {
			Connection con = null;
			String dest = getDestination(exc, counter);
			HostColonPort target = null;
			Integer responseStatusCode = null;
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
				boolean usingHttp2 = false;
				Http2Client h2c = null;
				SSLProvider sslProvider = getOutboundSSLProvider(exc, target);
				String sniServerName = getSNIServerName(exc);
				if (con == null && useHttp2) {
					h2c = http2ClientPool.reserveStream(target.host, target.port, sslProvider, sniServerName, proxy, proxySSLContext);
					if (h2c != null) {
						con = h2c.getConnection();
						usingHttp2 = true;
					}
				}
				if (con == null) {
					con = conMgr.getConnection(target.host, target.port, localAddr, sslProvider, connectTimeout,
							sniServerName, proxy, proxySSLContext, getApplicationProtocols());
					if (useHttp2 && Http2TlsSupport.isHttp2(con.socket))
						usingHttp2 = true;
					else
						exc.setTargetConnection(con);
					con.setKeepAttachedToExchange(usingHttp2 || exc.getRequest().isBindTargetConnectionToIncoming());
				}
				if (proxy != null && sslProvider == null)
					// if we use a proxy for a plain HTTP (=non-HTTPS) request, attach the proxy credentials.
					exc.getRequest().getHeader().setProxyAuthorization(proxy.getCredentials());
				Response response;

				if (usingHttp2) {
					if (h2c == null) {
						h2c = new Http2Client(con, sslProvider.showSSLExceptions());
						http2ClientPool.share(target.host, target.port, sslProvider, sniServerName, proxy, proxySSLContext, h2c);
					}
					response = h2c.doCall(exc, con);
					exc.setProperty(HTTP2, true);
					// TODO: handle CONNECT / AllowWebSocket / etc
					// TODO: connection should only be closed by the Http2Client
				} else {

					String newProtocol = null;

					if (exc.getRequest().isCONNECTRequest()) {
						handleConnectRequest(exc, con);
						response = Response.ok().build();
						newProtocol = "CONNECT";
						//TODO should we report to the httpClientStatusEventBus here somehow?
					} else {
						response = doCall(exc, con);
						if (trackNodeStatus)
							exc.setNodeStatusCode(counter, response.getStatusCode());

						newProtocol = upgradeProtocol(exc, response, newProtocol);
					}

					if (newProtocol != null) {
						setupConnectionForwarding(exc, con, newProtocol, streamPumpStats);
						exc.getDestinations().clear();
						exc.getDestinations().add(dest);
						con.setExchange(exc);
						exc.setResponse(response);
						return exc;
					}
				}

				responseStatusCode = response.getStatusCode();

				if (httpClientStatusEventBus != null)
					httpClientStatusEventBus.reportResponse(dest, responseStatusCode);

				if (!failOverOn5XX || !is5xx(responseStatusCode) || counter == maxRetries-1) {
					applyKeepAliveHeader(response, con);
					exc.getDestinations().clear();
					exc.getDestinations().add(dest);
					con.setExchange(exc);
					if (!usingHttp2)
						response.addObserver(con);
					exc.setResponse(response);
					//TODO should we report to the httpClientStatusEventBus here somehow?
					return exc;
				}

				// java.net.SocketException: Software caused connection abort: socket write error
			} catch (ConnectException e) {
				exception = e;
				log.info("Connection to " + (target == null ? dest : target) + " refused.");
			} catch(SocketException e){
				exception = e;
				if ( e.getMessage().contains("Software caused connection abort")) {
					log.info("Connection to " + dest + " was aborted externally. Maybe by the server or the OS Membrane is running on.");
				} else if (e.getMessage().contains("Connection reset") ) {
					log.info("Connection to " + dest + " was reset externally. Maybe by the server or the OS Membrane is running on.");
				} else {
					logException(exc, counter, e);
				}
			} catch (UnknownHostException e) {
				exception = e;
				log.warn("Unknown host: " + (target == null ? dest : target ));
			} catch (EOFWhileReadingFirstLineException e) {
				exception = e;
				log.debug("Server connection to " + dest + " terminated before line was read. Line so far: " + e.getLineSoFar());
			} catch (NoResponseException e) {
				exception = e;
			} catch (Exception e) {
				exception = e;
				logException(exc, counter, e);
			}
			finally	{
				if (trackNodeStatus) {
					if(exception != null){
						exc.setNodeException(counter, exception);
					}
				}
			}

			if (httpClientStatusEventBus != null) {
				//we have an error. either in the form of an exception, or as a 5xx response code.
				if (exception != null) {
					httpClientStatusEventBus.reportException(dest, exception);
				} else {
					assert responseStatusCode != null && is5xx(responseStatusCode);
					httpClientStatusEventBus.reportResponse(dest, responseStatusCode);
				}
			}

			if (exception instanceof UnknownHostException) {
				if (exc.getDestinations().size() < 2) {
					//don't retry this host, it's useless. (it's very unlikely that it will work after timeBetweenTriesMs)
					break;
				}
			} else if (exception instanceof NoResponseException) {
				//TODO explain why we give up here, don't even retry another host.
				//maybe it means we ourselves lost network connection?
				throw exception;
			}

			counter++;
			if (exc.getDestinations().size() == 1) {
				//as documented above, the sleep timeout is only applied between successive calls to the SAME destination.
				Thread.sleep(timeBetweenTriesMs);
			}
		}
		throw exception;
	}

	private String[] getApplicationProtocols() {
		if (useHttp2) {
			return HTTP2_PROTOCOLS;
		}
		return null;
	}

	private String upgradeProtocol(Exchange exc, Response response, String newProtocol) {
		if (exc.getProperty(ALLOW_WEBSOCKET) == TRUE && isUpgradeToResponse(response, "websocket")) {
			log.debug("Upgrading to WebSocket protocol.");
			return "WebSocket";
			//TODO should we report to the httpClientStatusEventBus here somehow?
		}
		if (exc.getProperty(ALLOW_TCP) == TRUE && isUpgradeToResponse(response, "tcp")) {
			log.debug("Upgrading to TCP protocol.");
			return "TCP";
		}
		if (exc.getProperty(ALLOW_SPDY) == TRUE && isUpgradeToResponse(response, "SPDY/3.1")) {
			log.debug("Upgrading to SPDY/3.1 protocol.");
			return "SPDY/3.1";
		}
		return newProtocol;
	}

	private String getSNIServerName(Exchange exc) {
		Object sniObject = exc.getProperty(SNI_SERVER_NAME);
		if (sniObject == null)
			return null;
		return (String) sniObject;
	}

	private boolean is5xx(Integer responseStatusCode) {
		return 500 <= responseStatusCode && responseStatusCode < 600;
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
			msg.append(ISO_8859_1.decode(ByteBuffer.wrap(baos.toByteArray())));

			if (e != null)
				log.debug("{}",msg, e);
			else
				log.debug("{}",msg);
		}
	}

	private Response doCall(Exchange exc, Connection con) throws IOException, EndOfStreamException {
		exc.getRequest().write(con.out, maxRetries > 1);
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
			WebSocketStreamPump aTemp = new WebSocketStreamPump(hsr.getSrcIn(), con.out, streamPumpStats, protocol + " " + source + " -> " + dest, exc.getRule(),true,exc);
			WebSocketStreamPump bTemp = new WebSocketStreamPump(con.in, hsr.getSrcOut(), streamPumpStats, protocol + " " + source + " <- " + dest, exc.getRule(),false, null);
			aTemp.init(bTemp);
			bTemp.init(aTemp);
			a = aTemp;
			b = bTemp;
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
			exc.getRequest().write(con.out, maxRetries > 1);
			Response response = new Response();
			response.read(con.in, false);
			log.debug("Status code response on CONNECT request: " + response.getStatusCode());
		}
		exc.getRequest().setUri(Constants.N_A);
	}

	private void do100ExpectedHandling(Exchange exc, Response response, Connection con) throws IOException, EndOfStreamException {
		exc.getRequest().getBody().write(exc.getRequest().getHeader().isChunked() ? new ChunkedBodyTransferrer(con.out) : new PlainBodyTransferrer(con.out), maxRetries > 1);
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

	@Override
	public void close() throws Exception {
		conMgr.shutdownWhenDone();
		if (http2ClientPool != null)
			http2ClientPool.shutdownWhenDone();
	}
}
