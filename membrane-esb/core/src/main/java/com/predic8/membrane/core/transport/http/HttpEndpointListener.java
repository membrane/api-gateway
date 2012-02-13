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
import java.net.BindException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.transport.PortOccupiedException;
import com.predic8.membrane.core.transport.SSLContext;

public class HttpEndpointListener extends Thread {

	protected static Log log = LogFactory.getLog(HttpEndpointListener.class.getName());

	private ServerSocket serverSocket;

	private HttpTransport transport;

	public HttpEndpointListener(int port, HttpTransport transport, SSLContext sslContext) throws IOException {
		this.transport = transport;

		try {
			if (sslContext != null)
				serverSocket = sslContext.createServerSocket(port);
			else
				serverSocket = new ServerSocket(port);
			
			log.debug("listening at port "+port);
		} catch (BindException e) {
			throw new PortOccupiedException(port);
		}
	}

	public void run() {
		while (serverSocket != null && !serverSocket.isClosed()) {
			try {
				transport.getExecutorService().execute(new HttpServerRunnable(serverSocket.accept(), transport));
			}
			catch (SocketException e) {
				if ("socket closed".endsWith(e.getMessage())) {
					log.debug("socket closed.");
					break;
				} else
					log.error(e);
			} catch (NullPointerException e) {
				// Ignore this. serverSocket variable is set null during a loop in the process of closing server socket.
			} catch (RejectedExecutionException e) {
				log.error("Max Thread pool size is exceeded. Please increase the property maxThreadPoolSize in monitor-beans.xml!");				
			} catch (Exception e) {
				log.error(e);
			}
		}
	}

	public void closePort() throws IOException {
		ServerSocket temp = serverSocket;
		serverSocket = null;
		if (!temp.isClosed()) {
			temp.close();
		}
	}
}