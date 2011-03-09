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

import static com.predic8.membrane.core.util.HttpUtil.getHost;
import static com.predic8.membrane.core.util.HttpUtil.getPort;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;

public class HttpClient {

	private static Log log = LogFactory.getLog(HttpClient.class.getName());

	private Socket socket;

	private InputStream in;

	private OutputStream out;

	private static final int MAX_CALL = 5;

	private boolean useProxy;
	
	private boolean useProxyAuth;
	
	private String proxyHost;
	
	private int proxyPort;
	
	private String proxyUser;
	
	private String proxyPassword;
	
	private boolean adjustHostHeader;
	
	private boolean isSameSocket(String host, int port) {
		if (!useProxy) {
			if ((host.equalsIgnoreCase(socket.getInetAddress().getHostName()) || host.equals(socket.getInetAddress().getHostAddress())) && port == socket.getPort()) {
				return true;
			}
		} else {
			if (socket.getInetAddress().getHostName().equalsIgnoreCase(proxyHost) && proxyPort == socket.getPort())
				return true;
		}
		
		return false;
	}

	private void openSocketIfNeeded(String host, int port, boolean stl) throws UnknownHostException, IOException {

		while (socket == null || socket.isClosed() || !isSameSocket(host, port)) {

			closeSocketAndStreams();

			if (useProxy) {
				log.debug("opening a new socket for host: " + proxyHost + " on port: " + proxyPort);
				createSocket(proxyHost, proxyPort, stl);
			} else {
				log.debug("opening a new socket for host: " + host + " on port: " + port);
				createSocket(host, port, stl);
			}

			log.debug("Opened connection on localPort: " + port);
			in = new BufferedInputStream(socket.getInputStream(), 2048);
			out = new BufferedOutputStream(socket.getOutputStream(), 2048);
		}

	}

	private void closeSocketAndStreams() throws IOException {
		if (in != null)
			in.close();

		if (out != null && !socket.isClosed()) {
			out.flush();
			out.close();
		}

		if (socket != null)
			socket.close();
	}

	private void createSocket(String host, int port, boolean stl) throws UnknownHostException, IOException {
		if (stl) {
			socket = SSLSocketFactory.getDefault().createSocket(host, port);
		} else {
			socket = new Socket(host, port);
		}
	}

	private void init(HttpExchange exc, int destIndex) throws UnknownHostException, IOException, MalformedURLException {
		String dest = exc.getDestinations().get(destIndex);
		
		exc.getRequest().setUri(dest);
		
		if (exc.getRequest().isCONNECTRequest()) {
			openSocketIfNeeded(getHost(dest), getPort(dest), getOutboundTLS(exc));
			return;
		}

		if (!useProxy) {
			exc.getRequest().setUri(getPathAndQueryString(dest));
		} else {
			if (useProxyAuth) {
				exc.getRequest().getHeader().setProxyAutorization(HttpUtil.getCredentials(proxyUser, proxyPassword));
			}
		}
			
		
		URL destination = new URL(dest);
		int targetPort = getTargetPort(destination);
		openSocketIfNeeded(destination.getHost(), targetPort, getOutboundTLS(exc));
		
		if (adjustHostHeader && exc.getRule() instanceof ForwardingRule)
			exc.getRequest().getHeader().setHost(destination.getHost() + ":" + targetPort);
	}

	private boolean getOutboundTLS(HttpExchange exc) {
		if (exc.getRule() == null)
			return false;
		return exc.getRule().isOutboundTLS();
	}

	private String getPathAndQueryString(String dest) throws MalformedURLException {
		URL url = new URL(dest);
		
		String uri = url.getPath();
		if (url.getQuery() != null) {
			return uri + "?" + url.getQuery();
		}
		return uri;
	}

	private int getTargetPort(URL url) throws MalformedURLException {
		if (url.getPort() == -1) {
			log.debug("URL Port is not set. Default target port 80 will be used.");
			return 80;
		}
		return url.getPort();
	}

	public Response call(HttpExchange exc) throws Exception {
		if (exc.getDestinations().size() == 0)
			throw new IllegalStateException("List of destinations is empty. Please specify at least one destination.");
		
		int counter = 0;
		Exception exception = null;
		while (counter < MAX_CALL) {
			try {
				log.debug("try # " + counter + " to " + exc.getDestinations().get(counter % exc.getDestinations().size()));
				init(exc, counter % exc.getDestinations().size());
				return doCall(exc);
			} catch (ConnectException ce) {
				exception = ce;
				log.debug(ce);
				if (socket != null)
					log.debug("Connection to " + socket.getInetAddress().getHostName() + " on port " + socket.getPort() + " refused.");
			} catch (Exception e) {
				log.debug("try # " + counter + " failed");
				exc.getRequest().writeStartLine(System.out);
				exc.getRequest().getHeader().write(System.out);
				e.printStackTrace();
				exception = e;
			}
			counter++;
			try {
				close();
				Thread.sleep(250);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		throw exception;
	}

	private Response doCall(HttpExchange exc) throws IOException, SocketException, EndOfStreamException {
		exc.setTimeReqSent(System.currentTimeMillis());

		if (exc.getRequest().isCONNECTRequest()) {
			handleConnectRequest(exc);
			return Response.createOKResponse();
		}

		exc.getRequest().write(out);

		if (exc.getRequest().isHTTP10()) {
			shutDownSourceSocket(exc);
		}

		Response res = new Response();
		try{
			res.read(in, !exc.getRequest().isHEADRequest());
		}catch(SocketException e){
			log.error("Connection aborted");
			exc.getRequest().write(System.err);
		}

		if (res.getStatusCode() == 100) {
			do100ExpectedHandling(exc, res);
		}

		exc.setReceived();
		exc.setTimeResReceived(System.currentTimeMillis());
		return res;
	}

	private void handleConnectRequest(HttpExchange exc) throws IOException, EndOfStreamException {
		if (useProxy) {
			exc.getRequest().write(out);
			Response response = new Response();
			response.read(in, false);
			log.debug("Status code response on CONNECT request: " + response.getStatusCode());
		}
		exc.getRequest().setUri(Constants.N_A);
		new TunnelThread(in, exc.getServerThread().getSrcOut(), "Onward Thread").start();
		new TunnelThread(exc.getServerThread().getSrcIn(), out, "Backward Thread").start();
	}

	private void do100ExpectedHandling(HttpExchange exc, Response response) throws IOException, EndOfStreamException {
		if (exc.getServerThread() instanceof HttpServerThread) {
			response.write(exc.getServerThread().srcOut);
		}
		exc.getRequest().readBody();
		exc.getRequest().getBody().write(out);
		response.read(in, !exc.getRequest().isHEADRequest());
	}

	private void shutDownSourceSocket(HttpExchange exc) throws IOException {
		exc.getServerThread().sourceSocket.shutdownInput();
		if (!socket.isOutputShutdown()) {
			log.info("Shutting down socket outputstream");
			socket.shutdownOutput();
		}
		// TODO close ?
	}

	public void close() throws IOException {
		if (socket == null || socket.isClosed())
			return;

		log.debug("Closing HTTP connection LocalPort: " + socket.getLocalPort());
		
		if (!(socket instanceof SSLSocket))
			socket.shutdownInput();
		
		socket.close();
	}
	
	public Socket getSocket() {
		return socket;
	}

	public boolean isUseProxy() {
		return useProxy;
	}

	public void setUseProxy(boolean useProxy) {
		this.useProxy = useProxy;
	}

	public boolean isUseProxyAuth() {
		return useProxyAuth;
	}

	public void setUseProxyAuth(boolean useProxyAuth) {
		this.useProxyAuth = useProxyAuth;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public String getProxyUser() {
		return proxyUser;
	}

	public void setProxyUser(String proxyUser) {
		this.proxyUser = proxyUser;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		this.proxyPassword = proxyPassword;
	}

	public boolean isAdjustHostHeader() {
		return adjustHostHeader;
	}

	public void setAdjustHostHeader(boolean adjustHostHeader) {
		this.adjustHostHeader = adjustHostHeader;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}
		
}
