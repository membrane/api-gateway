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

import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.flow.FlowController;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;

public class UserFeatureInterceptor extends AbstractInterceptor {

	private static final Log log = LogFactory.getLog(UserFeatureInterceptor.class.getName());
	private static final FlowController flowController = new FlowController();

	public UserFeatureInterceptor() {
		name = "User Feature";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Rule predecessorRule = exc.getRule();
		Outcome outcome = flowController.invokeRequestHandlers(exc, predecessorRule.getInterceptors());
		
		while (isTargetInternalAndContinue(exc, outcome)) {
			log.debug("routing to serviceProxy with name: " + getServiceProxyName(exc));

			// rule matching
			Rule newRule = getRuleByDest(exc.getDestinations().get(0));
			exc.setRule(newRule);
			// dispatching
			exc.getDestinations().clear();
			exc.getDestinations().add(getForwardingDestination(exc));
			// user feature
			outcome = flowController.invokeRequestHandlers(exc, newRule.getInterceptors());
		}
		exc.setRule(predecessorRule);
		return outcome;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		return Outcome.CONTINUE;
	}

	private String getServiceProxyName(Exchange exc) {
		return exc.getDestinations().get(0).substring(8);
	}

	private boolean isTargetInternalAndContinue(Exchange exc, Outcome outcome) {
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

	@Override
	public String getHelpId() {
		return "user-feature";
	}

}
