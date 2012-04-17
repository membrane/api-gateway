/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.servlet.embedded;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Enumeration;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.BodyWriter;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.AbortException;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import com.predic8.membrane.core.transport.http.ErrorReadingStartLineException;
import com.predic8.membrane.core.util.EndOfStreamException;

class HttpServletHandler extends AbstractHttpHandler {
	private static final Log log = LogFactory.getLog(HttpServletHandler.class);
	
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final InetAddress remoteAddr;
	
	public HttpServletHandler(HttpServletRequest request, HttpServletResponse response, 
			Transport transport) throws IOException {
		super(transport);
		this.request = request;
		this.response = response;
		this.remoteAddr = InetAddress.getByName(request.getRemoteAddr());
		this.exchange = new Exchange(this);
	}
	
	public void run() {
		try {
			srcReq = createRequest();

			exchange.received();

			try {
				exchange.setSourceHostname(getTransport().getRouter().getDnsCache().getHostName(remoteAddr));
				exchange.setSourceIp(getTransport().getRouter().getDnsCache().getHostAddress(remoteAddr));
				exchange.setRequest(srcReq);
				exchange.setOriginalRequestUri(srcReq.getUri());

				invokeRequestHandlers();
				invokeResponseHandlers(exchange);
			} catch (AbortException e) {
				exchange.finishExchange(true, exchange.getErrorMessage());
				writeResponse(exchange.getResponse());
				return;
			}

			writeResponse(exchange.getResponse());
			exchange.setCompleted();
			
			if (exchange.getTargetConnection() != null) {
				if (canKeepConnectionAlive(srcReq)) {
					exchange.getTargetConnection().release();
				} else {
					exchange.getTargetConnection().close();
				}
				exchange.setTargetConnection(null); // detach Connection from Exchange
			}
			
		} catch (EndOfStreamException e) {
			log.info("stream closed");
		} catch (ErrorReadingStartLineException e) {
			log.debug("Client connection terminated before line was read. Line so far: ("
					+ e.getStartLine() + ")");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private boolean canKeepConnectionAlive(Request srcReq) {
		return srcReq.isKeepAlive() && exchange.getResponse().isKeepAlive();
	}
	
	@SuppressWarnings("deprecation")
	protected void writeResponse(Response res) throws Exception {
		response.setStatus(res.getStatusCode(), res.getStatusMessage());
		for (HeaderField header : res.getHeader().getAllHeaderFields()) {
			if (header.getHeaderName().equals(Header.TRANSFER_ENCODING))
				continue;
			response.addHeader(header.getHeaderName().toString(), header.getValue());
		}
		
		ServletOutputStream out = response.getOutputStream();
		res.getBody().write(new BodyWriter(out));
		out.flush();
		
		response.flushBuffer();

		exchange.setTimeResSent(System.currentTimeMillis());
		exchange.collectStatistics();
	}

	private Request createRequest() throws IOException {
		Request srcReq = new Request();
		String pathQuery = request.getRequestURI();
		// remove context-path from path
		String contextPath = request.getContextPath();
		if (contextPath.length() > 0 && pathQuery.startsWith(contextPath))
			pathQuery = pathQuery.substring(contextPath.length());
		srcReq.create(
				request.getMethod(), 
				pathQuery, 
				request.getProtocol(), 
				createHeader(), 
				request.getInputStream());
		return srcReq;
	}

	private Header createHeader() {
		Header header = new Header();
		Enumeration<?> e = request.getHeaderNames();
		while (e.hasMoreElements()) {
			String key = (String)e.nextElement();
			Enumeration<?> e2 = request.getHeaders(key);
			while (e2.hasMoreElements()) {
				String value = (String)e2.nextElement();
				header.add(key, value);
			}
		}
		return header;
	}
	
	@Override
	public void shutdownInput() throws IOException {
		request.getInputStream().close();
		// nothing more we can do, since the servlet API does not give
		// us access to the TCP API
	}
	
	@Override
	public InetAddress getRemoteAddress() throws IOException {
		return remoteAddr;
	}
	
	@Override
	public int getLocalPort() {
		return 80; // this is not true, of course. We just always return 80.
		// This matches the default port 80, which is used in embedded-proxies.xml
		// by not specifying any port there. The port 80 is not actually
		// opened by ServletTransport, but will match the default 80 in
		// the ServiceProxyKey.
	}
}