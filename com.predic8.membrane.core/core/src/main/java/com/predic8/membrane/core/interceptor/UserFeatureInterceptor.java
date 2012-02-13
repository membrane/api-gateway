/* Copyright 2011 predic8 GmbH, www.predic8.com

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

import java.net.URL;
import java.util.Stack;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.*;

public class UserFeatureInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(UserFeatureInterceptor.class
			.getName());

	public UserFeatureInterceptor() {
		name = "User Feature";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		Stack<Interceptor> stack = new Stack<Interceptor>();

		Outcome outcome = invokeInterceptors(exc, stack);
		Rule predecessorRule = exc.getRule();
		while (isTargetInternalAndNotAborted(exc, outcome)) {

			log.debug("routing to serviceProxy with name: "
					+ getServiceProxyName(exc));

			exc.setRule(getRuleByDest(exc.getDestinations().get(0)));
			exc.getDestinations().clear();
			exc.getDestinations().add(getForwardingDestination(exc));
			outcome = invokeInterceptors(exc, stack);
		}
		exc.setRule(predecessorRule);
		exc.setProperty("interceptorStack", stack);
		return outcome;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {

		Interceptor i = getInterceptor(exc);
		Outcome outcome = Outcome.CONTINUE;
		while (i != null && outcome == Outcome.CONTINUE) {
			if (i.getFlow() != Flow.REQUEST) {

				log.debug("Invoking response handlers: " + i.getDisplayName()
						+ " on exchange: " + exc);

				outcome = i.handleResponse(exc);
			}
			i = getInterceptor(exc);
		}
		return outcome;
	}

	private String getServiceProxyName(Exchange exc) {
		return exc.getDestinations().get(0).substring(8);
	}

	private boolean isTargetInternalAndNotAborted(Exchange exc, Outcome outcome) {
		return outcome == Outcome.CONTINUE
				&& exc.getDestinations().get(0).startsWith("service:");
	}

	private String getForwardingDestination(Exchange exc) throws Exception {
		ServiceProxy p = (ServiceProxy) exc.getRule();
		if (p.getTargetURL() != null) {
			log.debug("destination: " + p.getTargetURL());
			return p.getTargetURL();
		}

		URL url = new URL("http", p.getTargetHost(), p.getTargetPort(), exc
				.getRequest().getUri());
		log.debug("destination: " + url);
		return "" + url;
	}

	private Rule getRuleByDest(String dest) {
		return router.getRuleManager().getRuleByName(dest.substring(8));
	}

	private Outcome invokeInterceptors(Exchange exc, Stack<Interceptor> stack)
			throws Exception {
		for (Interceptor i : exc.getRule().getInterceptors()) {
			stack.push(i);
			if (i.getFlow() == Flow.RESPONSE)
				continue;

			log.debug("Invoking request handlers: " + i.getDisplayName()
					+ " on exchange: " + exc);
			Outcome o = i.handleRequest(exc);
			if (o != Outcome.CONTINUE)
				return o;
		}
		return Outcome.CONTINUE;
	}

	private Interceptor getInterceptor(Exchange exc) {
		@SuppressWarnings("unchecked")
		Stack<Interceptor> stack = (Stack<Interceptor>) exc.getProperty("interceptorStack");
		if (stack.empty())
			return null;
		return stack.pop();
	}
}
