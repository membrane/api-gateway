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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.EndOfStreamException;
import static com.predic8.membrane.core.util.HttpUtil.*;

public class HttpClient {

	private static Log log = LogFactory.getLog(HttpClient.class.getName());

	private Socket socket;

	private InputStream in;

	private OutputStream out;

	private static final int MAX_CALL = 5;

	private Router router;
	
	private Configuration configuration = new Configuration();
	
	private boolean isSameSocket(String host, int port) {
		if (!useProxy()) {
			if ((host.equals(socket.getInetAddress().getHostName()) || host.equals(socket.getInetAddress().getHostAddress())) && port == socket.getPort()) {
				return true;
			}
		} else {
			if (getProxyHost().equals(socket.getInetAddress().getHostName()) && getProxyPort() == socket.getPort())
				return true;
		}
		
		return false;
	}

	private void openSocketIfNeeded(String host, int port) throws UnknownHostException, IOException {

		while (socket == null || socket.isClosed() || !isSameSocket(host, port)) {

			if (in != null)
				in.close();

			if (out != null && !socket.isClosed()) {
				out.flush();
				out.close();
			}

			if (socket != null)
				socket.close();

			if (useProxy()) {
				log.debug("opening a new socket for host: " + getProxyHost() + " on port: " + getProxyPort());
				socket = new Socket(getProxyHost(), getProxyPort());
			} else {
				log.debug("opening a new socket for host: " + host + " on port: " + port);
				socket = new Socket(host, port);
			}

			log.debug("Opened connection on localPort: " + port);
			in = new BufferedInputStream(socket.getInputStream(), 2048);
			out = new BufferedOutputStream(socket.getOutputStream(), 2048);
		}

	}

	private boolean useProxy() {
		return getConfiguration().getUseProxy();
	}

	private Configuration getConfiguration() {
		if (router == null)
			return configuration;
		return router.getConfigurationManager().getConfiguration();
	}

	private int getProxyPort() {
		return Integer.parseInt(getConfiguration().getProxyPort());
	}

	private String getProxyHost() {
		return getConfiguration().getProxyHost();
	}

	private void init(HttpExchange exc, int destIndex) throws UnknownHostException, IOException, MalformedURLException {
		String dest = exc.getDestinations().get(destIndex);
		
		exc.getRequest().setUri(dest);
		
		if (exc.getRequest().isCONNECTRequest()) {
			openSocketIfNeeded(getHost(dest), getPort(dest));
			return;
		}

		
		if (!useProxy())
			exc.getRequest().setUri(getPathAndQueryString(dest));

		URL destination = new URL(dest);
		openSocketIfNeeded(destination.getHost(), getTargetPort(destination));

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
		if (!url.getProtocol().equalsIgnoreCase("http"))
			throw new RuntimeException("Does not support protocol for URI: " + url.getPath());

		if (url.getPort() == -1)
			return 80;
		return url.getPort();
	}

	public Response call(HttpExchange exc) throws Exception {
		log.debug("calling using rule: " + exc.getRule() + " : " + exc.getRequest().getUri());
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

		Response response = new Response();
		response.read(in, !exc.getRequest().isHEADRequest());

		if (response.getStatusCode() == 100) {
			do100ExpectedHandling(exc, response);
		}

		exc.setReceived();
		exc.setTimeResReceived(System.currentTimeMillis());
		return response;
	}

	private void handleConnectRequest(HttpExchange exc) throws IOException, EndOfStreamException {
		if (useProxy()) {
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
		if (socket == null)
			return;

		log.debug("Closing HTTP connection LocalPort: " + socket.getLocalPort());
		socket.shutdownInput();
		socket.close();
	}

	public void setRouter(Router router) {
		this.router = router;
	}
	
	public Socket getSocket() {
		return socket;
	}
}
