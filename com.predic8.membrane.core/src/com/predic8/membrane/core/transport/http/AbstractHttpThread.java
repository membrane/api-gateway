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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.TerminateException;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
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

	protected Exchange exchange;
	
	protected Request srcReq;
	
	
	protected Socket sourceSocket;
	
	protected InputStream srcIn;
	
	protected OutputStream srcOut;
	
	
	protected HttpTransport transport;
	
	protected boolean stop = false;
	
	
	public AbstractHttpThread() {
		client = new HttpClient();
	}

	protected Outcome invokeRequestHandlers(List<Interceptor> interceptors) throws Exception {
		for (Interceptor interceptor : interceptors) {
			log.debug("Invoking request handlers:" + interceptor + " on exchange: " + exchange);
			if (interceptor.handleRequest(exchange) == Outcome.ABORT) {
				return Outcome.ABORT;
			}
		}
		return Outcome.CONTINUE;
	}


	protected Outcome invokeResponseHandlers(Exchange exchange, List<Interceptor> interceptors) throws Exception {
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

	protected List<Interceptor> getInterceptors() {
		List<Interceptor> list = new ArrayList<Interceptor>();
		list.addAll(transport.getInterceptors());
		list.addAll(exchange.getRule().getInterceptors());
		Collections.sort(list);
		return list;
	}
	
	protected List<Interceptor> getInterceptorsReverse() {
		List<Interceptor> list = getInterceptors();
		Collections.reverse(list);
		return list;
	}
	
	protected void setClientSettings() {
		if (transport == null)
			return;
		
		Configuration cfg = transport.getRouter().getConfigurationManager().getConfiguration();
		
		client.setAdjustHostHeader(cfg.getAdjustHostHeader());
		
		client.setProxy(cfg.getProxy());
	}
	
	protected void invokeResponseInterceptors() throws Exception, AbortException {
		if (Outcome.ABORT == invokeResponseHandlers(exchange, getInterceptorsReverse()))
			throw new AbortException();
	}

	protected void invokeRequestInterceptors(List<Interceptor> interceptors) throws Exception, AbortException {
		if (Outcome.ABORT == invokeRequestHandlers(interceptors))
			throw new AbortException();
	}
	
	protected void block(Message message) throws TerminateException {
		try {
			log.debug("message thread waits");
			message.wait();
			log.debug("message thread received notify");
			if (exchange.isForceToStop())
				throw new TerminateException("Force the exchange to stop.");
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
	protected void writeResponse(Response res) throws Exception{
		res.write(srcOut);
		srcOut.flush();
		exchange.setTimeResSent(System.currentTimeMillis());
	}


}
