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
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.config.Proxy;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;

public class HttpClient {

	private static final int TIME_BETWEEN_TRIES = 250;

	private static Log log = LogFactory.getLog(HttpClient.class.getName());

	private ConnectionManager conMgr = new ConnectionManager();
	
	private static final int MAX_TRIES = 5;

	private boolean adjustHostHeader;
	
	private Proxy proxy;
	
	private String host;
	
	private int port;
	
	private boolean tls;
	
	private String localHost;
	
	private boolean isUseProxy() {
		if (proxy == null)
			return false;
		return proxy.isUseProxy();
	}
	
	private void setRequestURI(Request req, String dest) throws MalformedURLException {
		if (isUseProxy() || req.isCONNECTRequest())
			req.setUri(dest);
		else
			req.setUri(HttpUtil.getPathAndQueryString(dest));
	}
	
	private void setHostAndPort(boolean connect, String dest) throws MalformedURLException {
		if (isUseProxy()) {
			port = proxy.getProxyPort();
			host = proxy.getProxyHost();
			return;
		}
		
		if (connect) {
			HostColonPort hcp = new HostColonPort(dest);
			port = hcp.port;
			host = hcp.host;
			return;
		} 
		
		URL destination = new URL(dest);
		port = HttpUtil.getPort(destination);
		host = destination.getHost();
		
	}
	
	private void init(Exchange exc, int destIndex) throws UnknownHostException, IOException, MalformedURLException {
		String dest = exc.getDestinations().get(destIndex);
		
		setRequestURI(exc.getRequest(), dest);
		setHostAndPort(exc.getRequest().isCONNECTRequest(), dest);
		
		if (isUseProxy() && proxy.isUseAuthentication()) {
			exc.getRequest().getHeader().setProxyAutorization(HttpUtil.getCredentials(proxy.getProxyUsername(), proxy.getProxyPassword()));
		} 
		
		tls = getOutboundTLS(exc);
		localHost = exc.getRule().getLocalHost();
		
		if (adjustHostHeader && exc.getRule() instanceof ForwardingRule) {
			URL destination = new URL(dest); //duplicate
			exc.getRequest().getHeader().setHost(destination.getHost() + ":" + port);
		}
	}

	private boolean getOutboundTLS(Exchange exc) {
		return exc.getRule().isOutboundTLS();
	}

	public Response call(Exchange exc) throws Exception {
		if (exc.getDestinations().size() == 0)
			throw new IllegalStateException("List of destinations is empty. Please specify at least one destination.");
		
		int counter = 0;
		Exception exception = null;
		while (counter < MAX_TRIES) {
			Connection con = null;
			int destIndex = counter % exc.getDestinations().size();
			String dest = exc.getDestinations().get(destIndex);
			try {
				log.debug("try # " + counter + " to " + dest);
				init(exc, destIndex);
				con = conMgr.getConnection(host, port, localHost, tls);
				return doCall(exc, con);
			} catch (ConnectException e) {
				exception = e;
				log.debug(e);
				if (con != null && con.socket != null)
					log.debug("Connection to " + dest + " on port " + con.socket.getPort() + " refused.");
			} catch (UnknownHostException e) {
				log.warn("Unknown host: " + host);
				exception = e;
				if (exc.getDestinations().size() < 2) {
					break; 
				}
			} catch (ErrorReadingStartLineException e) {
				log.warn("Server connection to " + dest + " terminated before start line was read. Start line so far: " + e.getStartLine());
				exception = e;
			} catch (Exception e) {
				logException(exc, counter, e);
				exception = e;
			}
			counter++;
			closeConnection(con);
			Thread.sleep(TIME_BETWEEN_TRIES);
		}
		throw exception;
	}

	private void closeConnection(Connection con) {
		try {
			close(con);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void logException(Exchange exc, int counter, Exception e) throws IOException {
		log.debug("try # " + counter + " failed");
		exc.getRequest().writeStartLine(System.out);
		exc.getRequest().getHeader().write(System.out);
		e.printStackTrace();
	}

	private Response doCall(Exchange exc, Connection con) throws IOException, SocketException, EndOfStreamException {
		exc.setTimeReqSent(System.currentTimeMillis());
		
		if (exc.getRequest().isCONNECTRequest()) {
			handleConnectRequest(exc, con);
			return Response.createOKResponse();
		}

		exc.getRequest().write(con.out);

		if (exc.getRequest().isHTTP10()) {
			shutDownSourceSocket(exc, con);
		}

		Response res = new Response();
		try{
			res.read(con.in, !exc.getRequest().isHEADRequest());
		}catch(SocketException e){
			log.error("Connection aborted");
			exc.getRequest().write(System.err);
		}

		if (res.getStatusCode() == 100) {
			do100ExpectedHandling(exc, res, con);
		}

		exc.setReceived();
		exc.setTimeResReceived(System.currentTimeMillis());
		return res;
	}

	private void handleConnectRequest(Exchange exc, Connection con) throws IOException, EndOfStreamException {
		if (isUseProxy()) {
			
			log.debug("host: " + host);
			log.debug("port: " + port);
			
			
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
		if (exc.getServerThread() instanceof HttpServerThread) {
			response.write(exc.getServerThread().srcOut);
		}
		exc.getRequest().readBody();
		exc.getRequest().getBody().write(con.out);
		response.read(con.in, !exc.getRequest().isHEADRequest());
	}

	private void shutDownSourceSocket(Exchange exc, Connection con) throws IOException {
		exc.getServerThread().sourceSocket.shutdownInput();
		if (!con.socket.isOutputShutdown()) {
			log.info("Shutting down socket outputstream");
			con.socket.shutdownOutput();
		}
		// TODO close ?
	}

	public void close(Connection con) throws IOException {
		if (con == null)
			return;
		
		con.close();
	}
	
	public void setAdjustHostHeader(boolean adjustHostHeader) {
		this.adjustHostHeader = adjustHostHeader;
	}
	
	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}
	
}
