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
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.predic8.membrane.core.exchange.HttpExchange;


public class HttpEndpointListener extends Thread {

	private ServerSocket serverSocket;
	private ExecutorService executorService;
	
	public HttpEndpointListener(int port) throws IOException {
		serverSocket = new ServerSocket(port);
		executorService = Executors.newCachedThreadPool();
	}

	public void run() {

		while (serverSocket != null && !serverSocket.isClosed()) {
			try {
				Socket socket = serverSocket.accept();
				HttpExchange exc = new HttpExchange();
				
				executorService.execute(new HttpServerThread(exc, socket));
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