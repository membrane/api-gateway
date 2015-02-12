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

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidParameterException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.transport.PortOccupiedException;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

public class HttpEndpointListener extends Thread {

	private static final Log log = LogFactory.getLog(HttpEndpointListener.class.getName());

	private final ServerSocket serverSocket;
	private final HttpTransport transport;
	private final SSLProvider sslProvider;
	private final ConcurrentHashMap<Socket, Boolean> idleSockets = new ConcurrentHashMap<Socket, Boolean>();
	private final ConcurrentHashMap<Socket, Boolean> openSockets = new ConcurrentHashMap<Socket, Boolean>();
	private volatile boolean closed;

	public HttpEndpointListener(String ip, int port, HttpTransport transport, SSLProvider sslProvider) throws IOException {
		this.transport = transport;
		this.sslProvider = sslProvider;

		try {
			if (sslProvider != null)
				serverSocket = sslProvider.createServerSocket(port, 50, ip != null ? InetAddress.getByName(ip) : null);
			else
				serverSocket = new ServerSocket(port, 50, ip != null ? InetAddress.getByName(ip) : null);
			
			setName("Connection Acceptor " + (ip != null ? ip + ":" : ":") + port);
			log.debug("listening at port "+port + (ip != null ? " ip " + ip : ""));
		} catch (BindException e) {
			throw new PortOccupiedException(port);
		}
	}

	public void run() {
		while (!closed) {
			try {
				Socket socket = serverSocket.accept();
				openSockets.put(socket, Boolean.TRUE);
				try {
					transport.getExecutorService().execute(new HttpServerHandler(socket, this));
				} catch (RejectedExecutionException e) {
					openSockets.remove(socket);
					log.error("HttpServerHandler execution rejected. Might be due to a proxies.xml hot deployment in progress or a low"
							+ " value for <transport maxThreadPoolSize=\"...\">.");
					socket.close();
				}
			}
			catch (SocketException e) {
				String message = e.getMessage();
				if (message != null && (message.endsWith("socket closed") || message.endsWith("Socket closed"))) {
					log.debug("socket closed.");
					break;
				} else
					log.error(e);
			} catch (NullPointerException e) {
				// Ignore this. serverSocket variable is set null during a loop in the process of closing server socket.
			} catch (Exception e) {
				log.error(e);
			}
		}
	}

	public void closePort() throws IOException {
		closed = true;
		if (!serverSocket.isClosed())
			serverSocket.close();
	}

	/**
	 * Closes all connections or those that are <i>currently</i> idle.
	 * @param onlyIdle whether to close only idle connections
	 * @return true, if there are no more open connections
	 */
	public boolean closeConnections(boolean onlyIdle) throws IOException {
		if (!closed)
			throw new IllegalStateException("please call closePort() fist.");
		for (Socket s : (onlyIdle ? idleSockets : openSockets).keySet())
			if (!s.isClosed())
				s.close();
		return openSockets.isEmpty();
	}

	void setIdleStatus(Socket socket, boolean isIdle) throws IOException {
		if (isIdle) {
			if (closed) {
				socket.close();
				throw new SocketException();
			}
			idleSockets.put(socket, Boolean.TRUE);
		} else {
			idleSockets.remove(socket);
		}
	}

	void setOpenStatus(Socket socket, boolean isOpen) {
		if (isOpen)
			throw new InvalidParameterException("isOpen");
		openSockets.remove(socket);
	}

	public HttpTransport getTransport() {
		return transport;
	}
	
	public boolean isClosed() {
		return closed;
	}
	
	public SSLProvider getSslProvider() {
		return sslProvider;
	}
}