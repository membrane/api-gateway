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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.HttpUtil;

public abstract class AbstractHttpThread extends Thread {

	protected static Log log;
	
	protected HttpClient client;

	protected Response targetRes;

	protected HttpExchange exchange;
	
	protected Request srcReq;
	
	
	protected Socket sourceSocket;
	
	protected InputStream srcIn;
	
	protected OutputStream srcOut;
	
	
	protected HttpTransport transport;
	
	protected boolean stop = false;
	
	
	public AbstractHttpThread() {
		client = new HttpClient();
	}

	protected Outcome invokeRequestHandlers(HttpExchange exchange, List<Interceptor> interceptors) throws Exception {
		for (Interceptor interceptor : interceptors) {
			log.debug("Invoking request handlers:" + interceptor + " on exchange: " + exchange);
			if (interceptor.handleRequest(exchange) == Outcome.ABORT) {
				return Outcome.ABORT;
			}
		}
		return Outcome.CONTINUE;
	}


	protected Outcome invokeResponseHandlers(HttpExchange exchange, List<Interceptor> interceptors) throws Exception {
		for (Interceptor interceptor : interceptors) {
			log.debug("Invoking response handlers :" + interceptor + " on exchange: " + exchange);
			if (interceptor.handleResponse(exchange) == Outcome.ABORT) {
				return Outcome.ABORT;
			}
		}
		return Outcome.CONTINUE;
	}

	
	public static Response createErrorResponse(String message) {
		Response res = new Response();
		res.setVersion("HTTP/1.1");
		res.setStatusCode(500);
		res.setStatusMessage("Internal Server Error");
		Header header = new Header();
		header.setContentType("text/xml;charset=utf-8");
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");

		res.setHeader(header);
		
		res.setBody(new Body("<message>" + message + "</message>"));
		return res;
	}
	
	public void stopThread() {
		stop = true;
	}

	public Socket getSourceSocket() {
		return sourceSocket;
	}

	public void setSourceSocket(Socket sourceSocket) {
		this.sourceSocket = sourceSocket;
	}

	public InputStream getSrcIn() {
		return srcIn;
	}

	public void setSrcIn(InputStream srcIn) {
		this.srcIn = srcIn;
	}

	public OutputStream getSrcOut() {
		return srcOut;
	}

	public void setSrcOut(OutputStream srcOut) {
		this.srcOut = srcOut;
	}

	public HttpTransport getTransport() {
		return transport;
	}

}
