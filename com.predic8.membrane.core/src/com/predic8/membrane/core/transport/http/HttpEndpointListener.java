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
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLServerSocketFactory;

import com.predic8.membrane.core.exchange.HttpExchange;


public class HttpEndpointListener extends Thread {

	private ServerSocket serverSocket;
	private ExecutorService executorService;
	
	private HttpTransport transport;
	
	public HttpEndpointListener(int port, HttpTransport transport, boolean tsl) throws IOException {
		if (tsl) {
			System.setProperty("javax.net.ssl.keyStore", "C:/work/membrane-monitor/com.predic8.membrane.core/configuration/client.jks");
			System.setProperty("javax.net.ssl.keyStorePassword", "secret");
			
			System.setProperty("javax.net.ssl.trustStore", "C:/work/membrane-monitor/com.predic8.membrane.core/configuration/client.jks");
			System.setProperty("javax.net.ssl.trustStorePassword", "secret");
			
			serverSocket = ((SSLServerSocketFactory)SSLServerSocketFactory.getDefault()).createServerSocket(port);
		} 
		
		else {
			serverSocket = new ServerSocket(port);
		}
		
		executorService = Executors.newCachedThreadPool();
		this.transport = transport;
	}

	public void run() {

		while (serverSocket != null && !serverSocket.isClosed() && transport != null) {
			try {
				executorService.execute(new HttpServerThread(new HttpExchange(), serverSocket.accept(), transport));
			} catch (SocketException e) {
				executorService.shutdown();
			} catch (IOException e) {
				executorService.shutdown();
				e.printStackTrace();
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