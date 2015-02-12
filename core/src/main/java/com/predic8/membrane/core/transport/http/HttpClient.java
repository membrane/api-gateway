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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.Util;

/**
 * Instances are thread-safe.
 */
public class HttpClient {

	private static Log log = LogFactory.getLog(HttpClient.class.getName());
	@GuardedBy("HttpClient.class")
	private static SSLProvider defaultSSLProvider;

	private final ProxyConfiguration proxy;
	private final AuthenticationConfiguration authentication;
	private final int timeBetweenTries = 250;
	private final int maxRetries;
	private final int connectTimeout;
	private final String localAddr;
	private final boolean allowWebSockets;

	private final ConnectionManager conMgr;
	
	public HttpClient() {
		this(new HttpClientConfiguration());
	}
	
	public HttpClient(HttpClientConfiguration configuration) {
		proxy = configuration.getProxy();
		authentication = configuration.getAuthentication();
		maxRetries = configuration.getMaxRetries();
		allowWebSockets = configuration.isAllowWebSockets();
		
		connectTimeout = configuration.getConnection().getTimeout();
		localAddr = configuration.getConnection().getLocalAddr();
		
		conMgr = new ConnectionManager(configuration.getConnection().getKeepAliveTimeout());
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
			req.setUri(HttpUtil.getPathAndQueryString(dest));
		}
	}
	
	private HostColonPort getTargetHostAndPort(boolean connect, String dest) throws MalformedURLException, UnknownHostException {
		if (proxy != null)
			return new HostColonPort(false, proxy.getHost(), proxy.getPort());
		
		if (connect)
			return new HostColonPort(false, dest);
		
		return new HostColonPort(new URL(dest));
	}
	
	private HostColonPort init(Exchange exc, String dest, boolean adjustHostHeader) throws UnknownHostException, IOException, MalformedURLException {
		setRequestURI(exc.getRequest(), dest);
		HostColonPort target = getTargetHostAndPort(exc.getRequest().isCONNECTRequest(), dest);
		
		if (proxy != null && proxy.isAuthentication()) {
			exc.getRequest().getHeader().setProxyAutorization(proxy.getCredentials());
		} 
		
		if (authentication != null) 
			exc.getRequest().getHeader().setAuthorization(authentication.getUsername(), authentication.getPassword());
		
		if (adjustHostHeader && (exc.getRule() == null || exc.getRule().isTargetAdjustHostHeader())) {
			URL d = new URL(dest);
			exc.getRequest().getHeader().setHost(d.getHost() + ":" + HttpUtil.getPort(d));
		}
		return target;
	}

	private SSLProvider getOutboundSSLProvider(Exchange exc, HostColonPort hcp) {
		if (exc.getRule() != null)
			return exc.getRule().getSslOutboundContext();
		if (hcp.useSSL)
			return getDefaultSSLProvider();
		return null;
	}

	private static synchronized SSLProvider getDefaultSSLProvider() {
		if (defaultSSLProvider == null)
			defaultSSLProvider = new SSLContext(new SSLParser(), null, null);
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
		while (counter < maxRetries) {
			Connection con = null;
			String dest = getDestination(exc, counter);
			HostColonPort target = null;
			try {
				log.debug("try # " + counter + " to " + dest);
				target = init(exc, dest, adjustHostHeader);
				InetAddress targetAddr = InetAddress.getByName(target.host);
				if (counter == 0) {
					con = exc.getTargetConnection();
					if (con != null) {
						if (!con.isSame(targetAddr, target.port)) {
							con.close();
							con = null;
						} else {
							con.setKeepAttachedToExchange(true);
						}
					}
				}
				if (con == null) {
					con = conMgr.getConnection(targetAddr, target.port, localAddr, getOutboundSSLProvider(exc, target), connectTimeout);
					con.setKeepAttachedToExchange(exc.getRequest().isBindTargetConnectionToIncoming());
					exc.setTargetConnection(con);
				}
				Response response;
				String newProtocol = null;
				
				if (exc.getRequest().isCONNECTRequest()) {
					handleConnectRequest(exc, con);
					response = Response.ok().build();
					newProtocol = "CONNECT";
				} else {
					response = doCall(exc, con);
					if (allowWebSockets && isUpgradeToWebSocketsResponse(response)) {
						log.debug("Upgrading to WebSocket protocol.");
						newProtocol = "WebSocket";
					}
				}
				
				if (newProtocol != null) {
					setupConnectionForwarding(exc, con, newProtocol);
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
			counter++;
			if (exc.getDestinations().size() == 1)
				Thread.sleep(timeBetweenTries);
		}
		throw exception;
	}

	private void applyKeepAliveHeader(Response response, Connection con) {
		String value = response.getHeader().getFirstValue(Header.KEEP_ALIVE);
		if (value == null)
			return;
		
		long timeout = Header.parseKeepAliveHeader(value, Header.TIMEOUT);
		if (timeout != -1)
			con.setTimeout(timeout * 1000);
		
		long max = Header.parseKeepAliveHeader(value, Header.MAX);
		if (max != -1 && max < con.getMaxExchanges())
			con.setMaxExchanges((int)max);
	}

	private String getDestination(Exchange exc, int counter) {
		return exc.getDestinations().get(counter % exc.getDestinations().size());
	}

	private void logException(Exchange exc, int counter, Exception e) throws IOException {
		StringBuilder msg = new StringBuilder();
		msg.append("try # ");
		msg.append(counter);
		msg.append(" failed\n");
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		exc.getRequest().writeStartLine(baos);
		exc.getRequest().getHeader().write(baos);
		msg.append(Constants.ISO_8859_1_CHARSET.decode(ByteBuffer.wrap(baos.toByteArray())));

		if (e != null)
			log.debug(msg, e);
		else
			log.debug(msg);
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

	public static void setupConnectionForwarding(Exchange exc, final Connection con, final String protocol) throws SocketException {
		final HttpServerHandler hsr = (HttpServerHandler)exc.getHandler();
		final StreamPump a = new StreamPump(con.in, hsr.getSrcOut());
		final StreamPump b = new StreamPump(hsr.getSrcIn(), con.out); 
		
		hsr.getSourceSocket().setSoTimeout(0);
		
		exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
			
			@Override
			public void setExchangeFinished() {
				String threadName = Thread.currentThread().getName();
				new Thread(a, threadName + " " + protocol + " Backward Thread").start();
				try {
					Thread.currentThread().setName(threadName + " " + protocol + " Onward Thread");
					b.run();
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

	private boolean isUpgradeToWebSocketsResponse(Response res) {
		return res.getStatusCode() == 101 && 
				"upgrade".equalsIgnoreCase(res.getHeader().getFirstValue(Header.CONNECTION)) &&
				"websocket".equalsIgnoreCase(res.getHeader().getFirstValue(Header.UPGRADE));
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
