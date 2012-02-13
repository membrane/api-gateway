/* Copyright 2009 predic8 GmbH, www.predic8.com

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
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Proxies;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.ProxyConfiguration;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.OKResponse;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.transport.SSLContext;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.Util;

/**
 * Instances are thread-safe.
 */
public class HttpClient {

	private static Log log = LogFactory.getLog(HttpClient.class.getName());

	private final ConnectionManager conMgr = new ConnectionManager();
	private final ProxyConfiguration proxy;
	private final int timeBetweenTries = 250;
	private final int maxRetries;
	private final boolean adjustHostHeader;
	
	public HttpClient() {
		proxy = null;
		maxRetries = 5;
		adjustHostHeader = false;
	}
	
	public HttpClient(Router router) {
		Proxies cfg = router.getConfigurationManager().getProxies();
		adjustHostHeader = cfg.getAdjustHostHeader();
		maxRetries = ((HttpTransport)router.getTransport()).getHttpClientRetries();
		proxy = cfg.getProxyConfiguration();
	}
	
	@Override
	protected void finalize() throws Throwable {
		conMgr.shutdownWhenDone();
	}
	
	private boolean useProxy() {
		if (proxy == null)
			return false;
		return proxy.useProxy();
	}
	
	private void setRequestURI(Request req, String dest) throws MalformedURLException {
		if (useProxy() || req.isCONNECTRequest())
			req.setUri(dest);
		else
			req.setUri(HttpUtil.getPathAndQueryString(dest));
	}
	
	private HostColonPort getTargetHostAndPort(boolean connect, String dest) throws MalformedURLException, UnknownHostException {
		if (useProxy())
			return new HostColonPort(proxy.getProxyHost(), proxy.getProxyPort());
		
		if (connect)
			return new HostColonPort(dest);
		
		return new HostColonPort(new URL(dest));
	}
	
	private HostColonPort init(Exchange exc, String dest) throws UnknownHostException, IOException, MalformedURLException {
		setRequestURI(exc.getRequest(), dest);
		HostColonPort target = getTargetHostAndPort(exc.getRequest().isCONNECTRequest(), dest);
		
		if (useProxy() && proxy.isUseAuthentication()) {
			exc.getRequest().getHeader().setProxyAutorization(proxy.getCredentials());
		} 
		
		if (adjustHostHeader && exc.getRule() instanceof ServiceProxy) {
			exc.getRequest().getHeader().setHost(new URL(dest).getHost() + ":" + target.port);
		}
		return target;
	}

	private SSLContext getOutboundSSLContext(Exchange exc) {
		return exc.getRule().getSslOutboundContext();
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
				con = conMgr.getConnection(InetAddress.getByName(target.host), target.port, exc.getRule().getLocalHost(), getOutboundSSLContext(exc));
				exc.setTargetConnection(con);
				return doCall(exc, con);
				// java.net.SocketException: Software caused connection abort: socket write error
			} catch (ConnectException e) {
				exception = e;
				log.info("Connection to " + (target == null ? dest : target ) + " refused.");
			} catch(SocketException e){
				if ( e.getMessage().contains("Software caused connection abort")) {
					log.info("Connection to " + dest + "was aborted externally. Maybe by the server or the OS Membrane is running on.");
				} else if (e.getMessage().contains("Connection reset") ) {
					log.info("Connection to " + dest + "was reset externally. Maybe by the server or the OS Membrane is running on.");
 				} else {
 					logException(exc, counter, e);
 				}
				exception = e;
			} catch (UnknownHostException e) {
				log.info("Unknown host: " + (target == null ? dest : target ));
				exception = e;
				if (exc.getDestinations().size() < 2) {
					break; 
				}
			} catch (ErrorReadingStartLineException e) {
				log.info("Server connection to " + dest + " terminated before line was read. Line so far: " + e.getStartLine());
				exception = e;
			} catch (Exception e) {
				logException(exc, counter, e);
				exception = e;
			}
			counter++;
			Thread.sleep(timeBetweenTries);
		}
		throw exception;
	}

	private String getDestination(Exchange exc, int counter) {
		return exc.getDestinations().get(counter % exc.getDestinations().size());
	}

	private void logException(Exchange exc, int counter, Exception e) throws IOException {
		log.debug("try # " + counter + " failed");
		exc.getRequest().writeStartLine(System.out);
		exc.getRequest().getHeader().write(System.out);
		e.printStackTrace();
	}

	private Response doCall(Exchange exc, Connection con) throws IOException, EndOfStreamException {
		if (exc.getRequest().isCONNECTRequest()) {
			handleConnectRequest(exc, con);
			return new OKResponse();
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
		new TunnelThread(con.in, exc.getServerThread().getSrcOut(), "Onward Thread").start();
		new TunnelThread(exc.getServerThread().getSrcIn(), con.out, "Backward Thread").start();
	}

	private void do100ExpectedHandling(Exchange exc, Response response, Connection con) throws IOException, EndOfStreamException {
		response.write(exc.getServerThread().srcOut);
		exc.getRequest().readBody();
		exc.getRequest().getBody().write(con.out);
		con.out.flush();
		response.read(con.in, !exc.getRequest().isHEADRequest());
	}

	private void shutDownRequestInputOutput(Exchange exc, Connection con) throws IOException {
		Util.shutdownInput(exc.getServerThread().sourceSocket);
		Util.shutdownOutput(con.socket);
	}
}
