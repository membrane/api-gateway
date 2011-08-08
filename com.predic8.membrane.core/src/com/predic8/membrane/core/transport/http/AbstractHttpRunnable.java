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

import java.io.*;
import java.net.Socket;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;

public abstract class AbstractHttpRunnable implements Runnable {

	private static Log log = LogFactory.getLog(AbstractHttpRunnable.class.getName());
	
	protected HttpClient client = new HttpClient();

	protected Response targetRes;

	protected Exchange exchange;
	
	protected Request srcReq;
	
	protected Socket sourceSocket;
	
	protected InputStream srcIn;
	
	protected OutputStream srcOut;
		
	protected HttpTransport transport;
	
	protected boolean stop = false;
	

	protected void invokeRequestHandlers() throws Exception {
		for (Interceptor i : transport.getInterceptors()) {
			
			log.debug("Handler flow: "+i.getDisplayName()+":"+i.getFlow());
			
			if (i.getFlow() == Flow.RESPONSE) continue;
			
			log.debug("Invoking request handler: " + i.getDisplayName() + " on exchange: " + exchange);
			
			if (i.handleRequest(exchange) == Outcome.ABORT) {
				throw new AbortException();
			}
		}
	}

	protected void invokeResponseHandlers(Exchange exc) throws Exception {
		
		for (int j = transport.getInterceptors().size()-1; j >= 0; j--) {
			
			Interceptor i = transport.getInterceptors().get(j);

			log.debug("Handler flow: "+i.getDisplayName()+":"+i.getFlow());
			
			if (i.getFlow() == Flow.REQUEST) continue;
			
			log.debug("Invoking response handler: " + i.getDisplayName() + " on exchange: " + exc);
			
			if (i.handleResponse(exc) == Outcome.ABORT) {
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

	protected void setClientSettings() {
		Proxies cfg = transport.getRouter().getConfigurationManager().getProxies();
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
