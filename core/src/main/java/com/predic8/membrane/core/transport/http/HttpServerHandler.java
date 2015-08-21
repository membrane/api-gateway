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

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MessageObserver;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import com.predic8.membrane.core.util.DNSCache;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.Util;

public class HttpServerHandler extends AbstractHttpHandler implements Runnable {

	private static final Log log = LogFactory.getLog(HttpServerHandler.class);
	private static final AtomicInteger counter = new AtomicInteger();

	private final HttpEndpointListener endpointListener;
	private Socket sourceSocket;
	private InputStream srcIn;
	private OutputStream srcOut;


	public HttpServerHandler(Socket socket, HttpEndpointListener endpointListener) throws IOException {
		super(endpointListener.getTransport());
		this.endpointListener = endpointListener;
		this.sourceSocket = socket;
	}

	@Override
	public HttpTransport getTransport() {
		return (HttpTransport)super.getTransport();
	}

	private void setup() throws IOException {
		this.exchange = new Exchange(this);
		SSLProvider sslProvider = endpointListener.getSslProvider();
		if (sslProvider != null)
			sourceSocket = sslProvider.wrapAcceptedSocket(sourceSocket);
		log.debug("New ServerThread created. " + counter.incrementAndGet());
		srcIn = new BufferedInputStream(sourceSocket.getInputStream(), 2048);
		srcOut = new BufferedOutputStream(sourceSocket.getOutputStream(), 2048);
		sourceSocket.setSoTimeout(endpointListener.getTransport().getSocketTimeout());
		sourceSocket.setTcpNoDelay(endpointListener.getTransport().isTcpNoDelay());
	}

	public void run() {
		Connection boundConnection = null; // see Request.isBindTargetConnectionToIncoming()
		try {
			updateThreadName(true);
			setup();
			while (true) {
				srcReq = new Request();

				endpointListener.setIdleStatus(sourceSocket, true);
				try {
					srcIn.mark(2);
					if (srcIn.read() == -1)
						break;
					srcIn.reset();
				} finally {
					endpointListener.setIdleStatus(sourceSocket, false);
				}

				if (boundConnection != null) {
					exchange.setTargetConnection(boundConnection);
					boundConnection = null;
				}

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
				boundConnection = exchange.getTargetConnection();
				exchange.setTargetConnection(null);
				if (!exchange.canKeepConnectionAlive())
					break;
				if (exchange.getResponse().isRedirect()) {
					break;
				}
				exchange.detach();
				exchange = new Exchange(this);
			}
		} catch (SocketTimeoutException e) {
			log.debug("Socket of thread " + counter + " timed out");
		} catch (SocketException se) {
			log.debug("client socket closed");
		} catch (SSLException s) {
			if (s.getCause() instanceof SSLException)
				s = (SSLException) s.getCause();
			if (s.getCause() instanceof SocketException)
				log.debug("ssl socket closed");
			else
				log.error("", s);
		} catch (IOException e) {
			log.error("", e);
		} catch (EndOfStreamException e) {
			log.debug("stream closed");
		} catch (AbortException e) {
			log.debug("exchange aborted.");
		} catch (NoMoreRequestsException e) {
			// happens at the end of a keep-alive connection
		} catch (NoResponseException e) {
			log.debug("No response received. Maybe increase the keep-alive timeout on the server.");
		} catch (EOFWhileReadingFirstLineException e) {
			log.debug("Client connection terminated before line was read. Line so far: ("
					+ e.getLineSoFar() + ")");
		} catch (Exception e) {
			log.error("", e);
		}

		finally {
			endpointListener.setOpenStatus(sourceSocket, false);

			if (boundConnection != null)
				try {
					boundConnection.close();
				} catch (IOException e) {
					log.debug("Closing bound connection.", e);
				}

			closeConnections();

			exchange.detach();

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
					+ sourceSocket.getInetAddress(), e2);
		}
	}

	private void process() throws Exception {
		try {

			DNSCache dnsCache = getTransport().getRouter().getDnsCache();
			InetAddress remoteAddr = sourceSocket.getInetAddress();
			String ip = dnsCache.getHostAddress(remoteAddr);
			exchange.setRemoteAddrIp(ip);
			exchange.setRemoteAddr(getTransport().isReverseDNS() ? dnsCache.getHostName(remoteAddr) : ip);

			exchange.setRequest(srcReq);
			exchange.setOriginalRequestUri(srcReq.getUri());

			if (exchange.getRequest().getHeader().is100ContinueExpected()) {
				final Request request = exchange.getRequest();
				request.addObserver(new MessageObserver() {
					public void bodyRequested(AbstractBody body) {
						try {
							if (request.getHeader().is100ContinueExpected()) {
								// request body from client so that interceptors can handle it
								Response.continue100().build().write(srcOut);
								// remove "Expect: 100-continue" since we already sent "100 Continue"
								request.getHeader().removeFields(Header.EXPECT);
							}
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}

					public void bodyComplete(AbstractBody body) {
					}
				});
			}

			invokeHandlers();

			exchange.blockResponseIfNeeded();
		} catch (AbortException e) {
			log.debug("Aborted");
			exchange.finishExchange(true, exchange.getErrorMessage());

			removeBodyFromBuffer();
			writeResponse(exchange.getResponse());

			log.debug("exchange set aborted");
			return;
		}

		removeBodyFromBuffer();
		writeResponse(exchange.getResponse());
		exchange.setCompleted();
		log.debug("exchange set completed");
	}

	/**
	 * Read the body from the client, if not already read.
	 *
	 * If the body has not already been read, the header includes
	 * "Expect: 100-continue" and the body has not already been sent by the
	 * client, nothing will be done. (Allowing the HTTP connection state to skip
	 * over the body transmission.)
	 */
	private void removeBodyFromBuffer() throws IOException {
		if (!exchange.getRequest().getHeader().is100ContinueExpected() || srcIn.available() > 0) {
			exchange.getRequest().readBody();
		}
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
	public InetAddress getLocalAddress() {
		return sourceSocket.getLocalAddress();
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

	public Socket getSourceSocket() {
		return sourceSocket;
	}

}