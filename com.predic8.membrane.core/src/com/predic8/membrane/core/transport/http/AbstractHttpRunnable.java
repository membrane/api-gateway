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
import java.util.List;

import org.apache.commons.logging.Log;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.TerminateException;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public abstract class AbstractHttpRunnable implements Runnable {

	protected static Log log;
	
	protected HttpClient client = new HttpClient();

	protected Response targetRes;

	protected Exchange exchange;
	
	protected Request srcReq;
	
	protected Socket sourceSocket;
	
	protected InputStream srcIn;
	
	protected OutputStream srcOut;
		
	protected HttpTransport transport;
	
	protected boolean stop = false;
	

	protected void invokeRequestHandlers(List<Interceptor> interceptors) throws Exception {
		for (Interceptor interceptor : interceptors) {
			log.debug("Invoking request handlers:" + interceptor + " on exchange: " + exchange);
			if (interceptor.handleRequest(exchange) == Outcome.ABORT) {
				throw new AbortException();
			}
		}
	}

	protected void invokeResponseHandlers(Exchange exchange, List<Interceptor> interceptors) throws Exception {
		for (Interceptor interceptor : interceptors) {
			log.debug("Invoking response handlers :" + interceptor + " on exchange: " + exchange);
			if (interceptor.handleResponse(exchange) == Outcome.ABORT) {
				throw new AbortException();
			}
		}
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
	
	protected List<Interceptor> getInterceptorsReverse(List<Interceptor> list) {
		Collections.reverse(list);
		return list;
	}
	
	protected void setClientSettings() {
		Configuration cfg = transport.getRouter().getConfigurationManager().getConfiguration();
		client.setAdjustHostHeader(cfg.getAdjustHostHeader());
		client.setProxy(cfg.getProxy());
		client.setMaxRetries(transport.getHttpClientRetries());
	}
	
	protected void block(Message msg) throws TerminateException {
		try {
			log.debug("Message thread waits");
			msg.wait();
			log.debug("Message thread received notify");
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
