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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.ProxyConfiguration;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ChunkedBodyTransferrer;
import com.predic8.membrane.core.http.PlainBodyTransferrer;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.transport.SSLContext;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.Util;

/**
 * Instances are thread-safe.
 */
public class HttpClient {

	private static Log log = LogFactory.getLog(HttpClient.class.getName());

	private final ConnectionManager conMgr;
	private final ProxyConfiguration proxy;
	private final int timeBetweenTries = 250;
	private final int maxRetries;
	private final boolean adjustHostHeader;
	private final boolean failOverOn5XX;
	
	public HttpClient() {
		this(true);
	}
	
	public HttpClient(boolean failOverOn5XX) {
		conMgr = new ConnectionManager(30000);
		proxy = null;
		maxRetries = 5;
		adjustHostHeader = false;
		this.failOverOn5XX = failOverOn5XX;
	}
	
	public HttpClient(Router router, boolean failOverOn5XX, long keepAliveTimeout, boolean adjustHostHeader, ProxyConfiguration proxyConfiguration) {
		conMgr = new ConnectionManager(keepAliveTimeout);
		this.adjustHostHeader = adjustHostHeader;
		maxRetries = router.getTransport().getHttpClientRetries();
		proxy = proxyConfiguration;
		this.failOverOn5XX = failOverOn5XX;
	}
	
	@Override
	protected void finalize() throws Throwable {
		conMgr.shutdownWhenDone();
	}
	
	private boolean useProxy() {
		if (proxy == null)
			return false;
		return proxy.isActive();
	}
	
	private void setRequestURI(Request req, String dest) throws MalformedURLException {
		if (useProxy() || req.isCONNECTRequest())
			req.setUri(dest);
		else
			req.setUri(HttpUtil.getPathAndQueryString(dest));
	}
	
	private HostColonPort getTargetHostAndPort(boolean connect, String dest) throws MalformedURLException, UnknownHostException {
		if (useProxy())
			return new HostColonPort(proxy.getHost(), proxy.getPort());
		
		if (connect)
			return new HostColonPort(dest);
		
		return new HostColonPort(new URL(dest));
	}
	
	private HostColonPort init(Exchange exc, String dest) throws UnknownHostException, IOException, MalformedURLException {
		setRequestURI(exc.getRequest(), dest);
		HostColonPort target = getTargetHostAndPort(exc.getRequest().isCONNECTRequest(), dest);
		
		if (useProxy() && proxy.isAuthentication()) {
			exc.getRequest().getHeader().setProxyAutorization(proxy.getCredentials());
		} 
		
		if (adjustHostHeader && exc.getRule() instanceof AbstractServiceProxy && ((AbstractServiceProxy)exc.getRule()).isTargetAdjustHostHeader()) {
			URL d = new URL(dest);
			exc.getRequest().getHeader().setHost(d.getHost() + ":" + HttpUtil.getPort(d));
		}
		return target;
	}

	private SSLContext getOutboundSSLContext(Exchange exc) {
		return exc.getRule() == null ? null : exc.getRule().getSslOutboundContext();
	}

	public Response call(Exchange exc) throws Exception {
		if (exc.getDestinations().size() == 0)
			throw new IllegalStateException("List of destinations is empty. Please specify at least one destination.");
		
		int counter = 0;
		Exception exception = null;
		while (counter < maxRetries) {
			Connection con = null;
			String dest = getDestination(exc, counter);
			HostColonPort target = null;
			try {
				log.debug("try # " + counter + " to " + dest);
				target = init(exc, dest);
				InetAddress targetAddr = InetAddress.getByName(target.host);
				if (counter == 0) {
					con = exc.getTargetConnection();
					if (con != null && !con.isSame(targetAddr, target.port))
						con = null;
				}
				if (con == null) {
					con = conMgr.getConnection(targetAddr, target.port, exc.getRule() == null ? null : exc.getRule().getLocalHost(), getOutboundSSLContext(exc));
					exc.setTargetConnection(con);
				}
				Response response = doCall(exc, con);
				boolean is5XX = 500 <= response.getStatusCode() && response.getStatusCode() < 600; 
				if (!failOverOn5XX || !is5XX || counter == maxRetries-1) {
					exc.getDestinations().clear();
					exc.getDestinations().add(dest);
					return response;
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
		if (exc.getRequest().isCONNECTRequest()) {
			handleConnectRequest(exc, con);
			return Response.ok().build();
		}
		
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

	private void handleConnectRequest(Exchange exc, Connection con) throws IOException, EndOfStreamException {
		if (useProxy()) {
			exc.getRequest().write(con.out);
			Response response = new Response();
			response.read(con.in, false);
			log.debug("Status code response on CONNECT request: " + response.getStatusCode());
		}
		exc.getRequest().setUri(Constants.N_A);
		HttpServerHandler hsr = (HttpServerHandler)exc.getHandler();
		new TunnelThread(con.in, hsr.getSrcOut(), "Onward Thread").start();
		new TunnelThread(hsr.getSrcIn(), con.out, "Backward Thread").start();
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
}
