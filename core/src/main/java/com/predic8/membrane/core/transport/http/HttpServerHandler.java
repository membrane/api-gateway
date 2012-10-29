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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.Util;

public class HttpServerHandler extends AbstractHttpHandler implements Runnable {

	private static final Log log = LogFactory.getLog(HttpServerHandler.class);
	private static final AtomicInteger counter = new AtomicInteger();
	
	private final Socket sourceSocket;
	private final InputStream srcIn;
	private final OutputStream srcOut;


	public HttpServerHandler(Socket socket, HttpTransport transport) throws IOException {
		super(transport);
		this.sourceSocket = socket;
		this.exchange = new Exchange(this);
		log.debug("New ServerThread created. " + counter.incrementAndGet());
		srcIn = new BufferedInputStream(sourceSocket.getInputStream(), 2048);
		srcOut = new BufferedOutputStream(sourceSocket.getOutputStream(), 2048);
		sourceSocket.setSoTimeout(transport.getSocketTimeout());
		sourceSocket.setTcpNoDelay(transport.isTcpNoDelay());
	}

	public HttpTransport getTransport() {
		return (HttpTransport)super.getTransport();
	}
	
	public void run() {
		Connection boundConnection = null; // see Request.isBindTargetConnectionToIncoming()
		try {
			updateThreadName(true);
			while (true) {
				boolean rebindConnection = false;
				if (boundConnection != null) {
					exchange.setTargetConnection(boundConnection);
					rebindConnection = true;
					boundConnection = null;
				}
				srcReq = new Request();
				srcReq.read(srcIn, true);

				exchange.received();

				if (srcReq.getHeader().getProxyConnection() != null) {
					srcReq.getHeader().add(Header.CONNECTION,
							srcReq.getHeader().getProxyConnection());
					srcReq.getHeader().removeFields(Header.PROXY_CONNECTION);
				}

				process();

				if (srcReq.isCONNECTRequest()) {
					log.debug("stopping HTTP Server Thread after establishing an HTTP connect");
					return;
				}
				boolean canKeepConnectionAlive = srcReq.isKeepAlive() && exchange.getResponse().isKeepAlive(); 
				if (exchange.getTargetConnection() != null) {
					if (canKeepConnectionAlive) {
						if (rebindConnection || srcReq.isBindTargetConnectionToIncoming())
							boundConnection = exchange.getTargetConnection();
						else
							exchange.getTargetConnection().release();
					} else {
						exchange.getTargetConnection().close();
					}
					exchange.setTargetConnection(null); // detach Connection from Exchange
				}
				if (!canKeepConnectionAlive)
					break;
				if (exchange.getResponse().isRedirect()) {
					break;
				}
				exchange.clearProperties();
				exchange = new Exchange(this);
			}
		} catch (SocketTimeoutException e) {
			log.debug("Socket of thread " + counter + " timed out");
		} catch (SocketException se) {
			log.debug("client socket closed");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (EndOfStreamException e) {
			log.info("stream closed");
		} catch (AbortException e) {
			log.info("exchange aborted.");
		} catch (NoMoreRequestsException e) {
			// happens at the end of a keep-alive connection
		} catch (ErrorReadingStartLineException e) {
			log.debug("Client connection terminated before line was read. Line so far: ("
					+ e.getStartLine() + ")");
		} catch (Exception e) {
			e.printStackTrace();
		}

		finally {
			if (boundConnection != null)
				try {
					boundConnection.close();
				} catch (IOException e) {
					log.debug("Closing bound connection.", e);
				}
			
			if (srcReq.isCONNECTRequest())
				return;

			closeConnections();
			
			exchange.clearProperties();
			
			updateThreadName(false);
		}

	}

	private void closeConnections() {

		try {
			if (!sourceSocket.isClosed()) {
				if (!(sourceSocket instanceof SSLSocket))
					sourceSocket.shutdownOutput();
				sourceSocket.close();
			}
		} catch (Exception e2) {
			log.error("problems closing socket on remote port: "
					+ sourceSocket.getPort() + " on remote host: "
					+ sourceSocket.getInetAddress());
			e2.printStackTrace();
		}
	}

	private void process() throws Exception {
		try {

			exchange.setSourceHostname(getTransport().getRouter().getDnsCache()
					.getHostName(sourceSocket.getInetAddress()));
			exchange.setSourceIp(getTransport().getRouter().getDnsCache()
					.getHostAddress(sourceSocket.getInetAddress()));

			exchange.setRequest(srcReq);
			exchange.setOriginalRequestUri(srcReq.getUri());
			
			if (getTransport().isAutoContinue100Expected() && exchange.getRequest().getHeader().is100ContinueExpected())
				tellClientToContinueWithBody();

			invokeHandlers();
			
			exchange.blockResponseIfNeeded();
		} catch (AbortException e) {
			log.debug("Aborted");
			exchange.finishExchange(true, exchange.getErrorMessage());
			
			exchange.getRequest().readBody(); // read if not alread read
			writeResponse(exchange.getResponse());

			log.debug("exchange set aborted");
			return;
		}

		exchange.getRequest().readBody(); // read if not alread read
		writeResponse(exchange.getResponse());
		exchange.setCompleted();
		log.debug("exchange set completed");

	}

	private void tellClientToContinueWithBody() throws IOException {
		// request body from client so that interceptors can handle it
		Response.continue100().build().write(srcOut);
		// remove "Expect: 100-continue" since we already sent "100 Continue"
		exchange.getRequest().getHeader().removeFields(Header.EXPECT);
	}
	
	private void updateThreadName(boolean fromConnection) {
		if (fromConnection) {
			StringBuilder sb = new StringBuilder();
			sb.append(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
			sb.append(" ");
			InetAddress ia = sourceSocket.getInetAddress();
			if (ia != null)
				sb.append(ia.toString());
			sb.append(":");
			sb.append(sourceSocket.getPort());
			Thread.currentThread().setName(sb.toString());
		} else {
			Thread.currentThread().setName(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
		}
	}
	
	protected void writeResponse(Response res) throws Exception{
		res.write(srcOut);
		srcOut.flush();
		exchange.setTimeResSent(System.currentTimeMillis());
		exchange.collectStatistics();
	}
	
	@Override
	public void shutdownInput() throws IOException {
		Util.shutdownInput(sourceSocket);	
	}
	
	@Override
	public InetAddress getRemoteAddress() throws IOException {
		return sourceSocket.getInetAddress();
	}
	
	@Override
	public int getLocalPort() {
		return sourceSocket.getLocalPort();
	}
	
	public InputStream getSrcIn() {
		return srcIn;
	}

	public OutputStream getSrcOut() {
		return srcOut;
	}

}