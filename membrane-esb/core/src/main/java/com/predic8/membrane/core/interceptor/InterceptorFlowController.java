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

/**
 * Controls the flow of an exchange through a chain of interceptors.
 * 
 * In the trivial setup, an exchange passes through two chains until it hits
 * Outcome.RETURN: The main chain owned by the Transport (containing the
 * RuleMatching, Dispatching, UserFeature and HttpClient-Interceptors) and the
 * inner chain owned by the UserFeatureInterceptor (containing any interceptor
 * configured in proxies.xml).
 * 
 * The {@link HTTPClientInterceptor}, the last interceptor in the main chain,
 * always returns {@link Outcome#RETURN} or {@link Outcome#ABORT}, never
 * {@link Outcome#CONTINUE}.
 * 
 * Any chain is followed using {@link Interceptor#handleRequest(Exchange)} until
 * it hits {@link Outcome#RETURN} or {@link Outcome#ABORT}. As the chain is
 * followed, every interceptor (except those with {@link Flow#REQUEST}) are
 * added to the exchange's stack.
 * 
 * When {@link Outcome#RETURN} is hit, the exchange's interceptor stack is
 * unwound and {@link Interceptor#handleResponse(Exchange)} is called for every
 * interceptor on it.
 * 
 * When {@link Outcome#ABORT} is hit, handling is aborted: An
 * {@link AbortException} is thrown and the stack is not unwound. (Note that
 * this is subject to change: In some future implementation, the stack is to be
 * unwound in this case and a new method handleAbort(Exchange) is to be called
 * for every interceptor on it.)
 */
public class InterceptorFlowController {
	
	private static final Log log = LogFactory.getLog(InterceptorFlowController.class);

	/**
	 * Runs both the request and response handlers: This executes the main interceptor chain.
	 */
	public void invokeHandlers(Exchange exchange, List<Interceptor> interceptors) throws Exception {
		switch (invokeRequestHandlers(exchange, interceptors)) {
		case CONTINUE:
			throw new Exception("The last interceptor in the main chain may not return CONTINUE. Change it to RETURN.");
		case RETURN:
			break;
		case ABORT:
			throw new AbortException();
		}
		invokeResponseHandlers(exchange);
	}
	
	/**
	 * Runs the request handlers of the given chain. Response handlers are collected as
	 * the request handlers are executed and appended to the exchange's interceptor stack
	 * for later unwinding.
	 */
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
	
	/**
	 * Runs all response handlers for interceptors that have been collected on
	 * the exchange's stack so far.
	 */
	private void invokeResponseHandlers(Exchange exchange) throws Exception {
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
