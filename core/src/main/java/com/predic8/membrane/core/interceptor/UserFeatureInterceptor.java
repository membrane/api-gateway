/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.Rule;

/**
 * Handles features that are user-configured in proxies.xml .
 * 
 * Not that we do not implement handleResponse() as this will be
 * automatically done by the stack-unwinding in {@link InterceptorFlowController}.
 */
@MCInterceptor(name="userFeature")
public class UserFeatureInterceptor extends AbstractInterceptor {

	private static final Log log = LogFactory.getLog(UserFeatureInterceptor.class.getName());
	private static final InterceptorFlowController flowController = new InterceptorFlowController();

	public UserFeatureInterceptor() {
		name = "User Feature";
		setFlow(Flow.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Rule predecessorRule = exc.getRule();
		Outcome outcome = flowController.invokeRequestHandlers(exc, predecessorRule.getInterceptors());
		
		while (isTargetInternalAndContinue(exc, outcome)) {
			log.debug("routing to serviceProxy with name: " + getServiceProxyName(exc));

			// rule matching
			String destination = exc.getDestinations().get(0);
			Rule newRule = getRuleByDest(destination);
			if (newRule == null)
				throw new Exception("No proxy found for destination " + destination);
			exc.setRule(newRule);
			// dispatching
			exc.getDestinations().clear();
			exc.getDestinations().add(DispatchingInterceptor.getForwardingDestination(exc));
			// user feature
			outcome = flowController.invokeRequestHandlers(exc, newRule.getInterceptors());
		}
		exc.setRule(predecessorRule);
		return outcome;
	}

	private String getServiceProxyName(Exchange exc) {
		return exc.getDestinations().get(0).substring(8);
	}

	private boolean isTargetInternalAndContinue(Exchange exc, Outcome outcome) {
		return outcome == Outcome.CONTINUE
				&& exc.getDestinations().get(0).startsWith("service:");
	}

	private Rule getRuleByDest(String dest) {
		return router.getRuleManager().getRuleByName(dest.substring(8));
	}

	@Override
	public String getHelpId() {
		return "user-feature";
	}

}
