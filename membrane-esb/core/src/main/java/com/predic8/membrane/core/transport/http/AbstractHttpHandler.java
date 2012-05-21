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
	
	
	protected void invokeHandlers() throws Exception {
		boolean logDebug = log.isDebugEnabled();

		int j = 0;
outer:  for (;j < transport.getInterceptors().size(); j++) {
			
			Interceptor i = transport.getInterceptors().get(j);
			
			if (logDebug)
				log.debug("Handler flow: "+i.getDisplayName()+":"+i.getFlow());
			
			if (i.getFlow() == Flow.RESPONSE) continue;
			
			if (logDebug)
				log.debug("Invoking request handler: " + i.getDisplayName() + " on exchange: " + exchange);
			
			switch (i.handleRequest(exchange)) {
				case ABORT:
					throw new AbortException();
				case RETURN:
					break outer;
			}
		}
		invokeResponseHandlers(j-1);
		
	}

	private void invokeResponseHandlers(int start) throws Exception {
		boolean logDebug = log.isDebugEnabled();
		
		for (int j = start; j >= 0; j--) {
			
			Interceptor i = transport.getInterceptors().get(j);

			if (logDebug)
				log.debug("Handler flow: "+i.getDisplayName()+":"+i.getFlow());
			
			if (i.getFlow() == Flow.REQUEST) continue;
			
			if (logDebug)
				log.debug("Invoking response handler: " + i.getDisplayName() + " on exchange: " + exchange);
			
			if (i.handleResponse(exchange) == Outcome.ABORT) {
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
