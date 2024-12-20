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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.transport.http.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * Controls the flow of an exchange through a chain of interceptors.
 * <p>
 * In the trivial setup, an exchange passes through two chains until it hits
 * RETURN: The main chain owned by the Transport (containing the
 * RuleMatching, Dispatching, UserFeature and HttpClient-Interceptors) and the
 * inner chain owned by the UserFeatureInterceptor (containing any interceptor
 * configured in proxies.xml).
 * <p>
 * The {@link HTTPClientInterceptor}, the last interceptor in the main chain,
 * always returns {@link Outcome#RETURN} or {@link Outcome#ABORT}, never
 * {@link Outcome#CONTINUE}.
 * <p>
 * Any chain is followed using {@link Interceptor#handleRequest(Exchange)} until
 * it hits {@link Outcome#RETURN} or {@link Outcome#ABORT}. As the chain is
 * followed, every interceptor (except those with {@link Flow#REQUEST}) are
 * added to the exchange's stack.
 * <p>
 * When {@link Outcome#RETURN} is hit, the exchange's interceptor stack is
 * unwound and {@link Interceptor#handleResponse(Exchange)} is called for every
 * interceptor on it.
 * <p>
 * When {@link Outcome#ABORT} is hit, handling is aborted: An
 * {@link AbortException} is thrown. The stack is unwound calling
 * {@link Interceptor#handleAbort(Exchange)} on each interceptor on it.
 */
public class NonStackInterceptorFlowController extends  InterceptorFlowController {

	private static final Logger log = LoggerFactory.getLogger(NonStackInterceptorFlowController.class);

	/**
	 * Runs both the request and response handlers: This executes the main interceptor chain.
	 */
	public void invokeHandlers(Exchange exchange, List<Interceptor> interceptors) throws Exception {
		System.out.println("NonStackInterceptorFlowController.invokeHandlers");

		for (int i = 0; i < interceptors.size(); i++) {

			Interceptor interceptor = interceptors.get(i);
			System.out.println("----------------");
			System.out.println("Interceptor: " + interceptor.getDisplayName());

			EnumSet<Flow> f = interceptor.getFlow();
			System.out.println("f = " + f);
			if (!f.contains(REQUEST))
				continue;

			Outcome o = interceptor.handleRequest(exchange);

			if (o == RETURN) {
				System.out.println("******** invoke Handlers: Returning");
				runReturn(exchange, interceptors, i);
				return;
			}
			if (o == ABORT) {
				runAbort(exchange, interceptors, i);
				return;
			}
		}
	}

	/**
	 * Runs the request handlers of the given chain.
	 */
	public Outcome invokeRequestHandlers(Exchange exchange, List<Interceptor> interceptors)
			throws Exception {

		System.out.println("=======================");
		System.out.println("NonStackInterceptorFlowController.invokeRequestHandlers");

		for (int i = 0; i < interceptors.size(); i++) {

			Interceptor interceptor = interceptors.get(i);
			System.out.println("----------------");
			System.out.println("Interceptor %d: %s".formatted(i, interceptor.getDisplayName()));

			EnumSet<Flow> f = interceptor.getFlow();
			System.out.println("f = " + f);
			if (!f.contains(REQUEST))
				continue;

			Outcome o;
			try {
				o = interceptor.handleRequest(exchange);
			} catch (Throwable t) {
				System.out.println("Exception !!!!!!!!!!!!!!");
				runAbort(exchange, interceptors, i);
				return ABORT;
			}

			System.out.println("outcome = " + o);
			if (o == RETURN) {
				runReturn(exchange, interceptors, i);
				return RETURN;
			}
			if (o == ABORT) {
				runAbort(exchange, interceptors, i);
				return ABORT;
			}
		}
		return CONTINUE;
	}

	private static void runReturn(Exchange exchange, List<Interceptor> interceptors, int i) throws Exception {
		boolean aborted = false;
		for (int j = i - 1 ; j >= 0; j--) {
			Interceptor backI = interceptors.get(j);
			System.out.println("------<-<-<--------!");
			System.out.println("Back %d: %s".formatted(j, backI.getDisplayName()));
			Outcome o = null;
			if (!aborted) {
				o = backI.handleResponse(exchange);
			} else {
				backI.handleAbort(exchange);
			}
			if (o == ABORT) {
				aborted = true;
			}
		}
	}

	private static void runAbort(Exchange exchange, List<Interceptor> interceptors, int i) throws Exception {
		for (int j = i - 1 ; j >= 0; j--) {
			Interceptor backI = interceptors.get(j);
			System.out.println("------Abort--------");
			System.out.println("Abort %d: %s".formatted(i, backI.getDisplayName()));
			backI.handleAbort(exchange);
		}
	}

	/**
	 * Runs all response handlers for interceptors that have been collected on
	 * the exchange's stack so far.
	 */
	public void invokeResponseHandlers(Exchange exchange, List<Interceptor> interceptors) throws Exception {

		boolean aborted = false;

		for (int i = interceptors.size() - 1; i >= 0; i--) {
			Interceptor interceptor = interceptors.get(i);
			if (!interceptor.getFlow().contains(RESPONSE))
				continue;

			if (!aborted) {
				if (interceptor.handleResponse(exchange) == ABORT)
					aborted = true;
			} else {
				interceptor.handleAbort(exchange);
			}
		}
	}
}