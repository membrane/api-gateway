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
package com.predic8.membrane.core.interceptor;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.AbortException;

public class InterceptorFlowController {
	
	private static final Log log = LogFactory.getLog(InterceptorFlowController.class);

	public void invokeHandlers(Exchange exchange, List<Interceptor> interceptors) throws Exception {
		switch (invokeRequestHandlers(exchange, interceptors)) {
		case CONTINUE:
			// last interceptor in main chain: treat CONTINUE as RETURN.
			break;
		case RETURN:
			break;
		case ABORT:
			throw new AbortException();
		}
		invokeResponseHandlers(exchange);
	}
	
	public Outcome invokeRequestHandlers(Exchange exchange, List<Interceptor> interceptors)
			throws Exception {
		boolean logDebug = log.isDebugEnabled();

		for (Interceptor i : interceptors) {
			if (logDebug)
				log.debug("Handler flow: " + i.getDisplayName() + ":" + i.getFlow());

			switch (i.getFlow()) {
			case REQUEST:
				break;
			case RESPONSE:
				exchange.pushInterceptorToStack(i);
				continue;
			case REQUEST_RESPONSE:
				exchange.pushInterceptorToStack(i);
				break;
			}

			if (logDebug)
				log.debug("Invoking request handler: " + i.getDisplayName() + " on exchange: " + exchange);

			switch (i.handleRequest(exchange)) {
			case CONTINUE:
				break;
			case ABORT:
				return Outcome.ABORT;
			case RETURN:
				return Outcome.RETURN;
			}
		}
		return Outcome.CONTINUE;
	}
	
	public void invokeResponseHandlers(Exchange exchange) throws Exception {
		boolean logDebug = log.isDebugEnabled();
		
		Interceptor i;
		while ((i = exchange.popInterceptorFromStack()) != null) {
			if (logDebug)
				log.debug("Handler flow: "+i.getDisplayName()+":"+i.getFlow());
			
			if (logDebug)
				log.debug("Invoking response handler: " + i.getDisplayName() + " on exchange: " + exchange);
			
			if (i.handleResponse(exchange) == Outcome.ABORT) {
				throw new AbortException();
			}
		}
	}

}
