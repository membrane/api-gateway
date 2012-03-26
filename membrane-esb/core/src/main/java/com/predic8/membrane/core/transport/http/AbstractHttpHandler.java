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
import java.net.InetAddress;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.Transport;

public abstract class AbstractHttpHandler  {

	private static Log log = LogFactory.getLog(AbstractHttpHandler.class.getName());
	
	protected Exchange exchange;
	protected Request srcReq;
		
	private final Transport transport;
	
	public AbstractHttpHandler(Transport transport) {
		this.transport = transport;
	}
	
	protected void invokeInterceptors(Flow f, List<Interceptor> list, int start, int end, int step) throws Exception {
		boolean logDebug = log.isDebugEnabled();
		
		for (int j = start; j != transport.getInterceptors().size(); j+=step) {
			Interceptor i = transport.getInterceptors().get(j);
			
			if (i.getFlow() != Flow.REQUEST_RESPONSE && 
				i.getFlow() != f) continue;
			
			if (logDebug)
				log.debug("Flow: "+f+". Invoking: " + i.getDisplayName() + " on exchange: " + exchange);
			
			Outcome outcome;
			if (f == Flow.REQUEST) {
				outcome = i.handleRequest(exchange);
			} else {
				outcome = i.handleResponse(exchange);
			}
			
			if ( outcome == Outcome.ABORT) {
				throw new AbortException();
			}
		} 		
	}
	protected void invokeRequestHandlers() throws Exception {
		boolean logDebug = log.isDebugEnabled();

		for (Interceptor i : transport.getInterceptors()) {
			
			if (logDebug)
				log.debug("Handler flow: "+i.getDisplayName()+":"+i.getFlow());
			
			if (i.getFlow() == Flow.RESPONSE) continue;
			
			if (logDebug)
				log.debug("Invoking request handler: " + i.getDisplayName() + " on exchange: " + exchange);
			
			if (i.handleRequest(exchange) == Outcome.ABORT) {
				throw new AbortException();
			}
		} 
	}

	protected void invokeResponseHandlers(Exchange exc) throws Exception {
		boolean logDebug = log.isDebugEnabled();
		
		for (int j = transport.getInterceptors().size()-1; j >= 0; j--) {
			
			Interceptor i = transport.getInterceptors().get(j);

			if (logDebug)
				log.debug("Handler flow: "+i.getDisplayName()+":"+i.getFlow());
			
			if (i.getFlow() == Flow.REQUEST) continue;
			
			if (logDebug)
				log.debug("Invoking response handler: " + i.getDisplayName() + " on exchange: " + exc);
			
			if (i.handleResponse(exc) == Outcome.ABORT) {
				throw new AbortException();
			}
		}
	}

	public Transport getTransport() {
		return transport;
	}

	public abstract void shutdownInput() throws IOException;
	public abstract InetAddress getRemoteAddress() throws IOException;
	public abstract int getLocalPort();

}
