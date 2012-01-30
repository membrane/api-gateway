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

package com.predic8.membrane.core.interceptor;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.NullRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class RuleMatchingInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(RuleMatchingInterceptor.class.getName());

	private boolean xForwardedForEnabled = true;

	public RuleMatchingInterceptor() {
		name = "Rule Matching Interceptor";		
		setFlow(Flow.REQUEST);
	}
	
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRule() != null ) return Outcome.CONTINUE;
		
		Rule rule = getRule(exc);
		exc.setRule(rule);
		
		if (rule instanceof NullRule) {
			handleNoRuleFound(exc);
			return Outcome.ABORT;
		}
		
		if (xForwardedForEnabled && (rule instanceof ServiceProxy))
			insertXForwardedFor(exc);

		return Outcome.CONTINUE;
	}

	private void handleNoRuleFound(Exchange exc) throws IOException {
		exc.getRequest().readBody();
		exc.getServerThread().getSourceSocket().shutdownInput();
		exc.setResponse(Response.interalServerError("This request was not accepted by Membrane Monitor. Please correct the request and try again.").build());
	}

	private Rule getRule(Exchange exc) {
		ServiceProxyKey key = exc.getServiceProxyKey();

		Rule rule = router.getRuleManager().getMatchingRule(key);
		if (rule != null) {
			log.debug("Matching Rule found for RuleKey " + key);
			return rule;
		}

		return findProxyRule(exc);
	}

	private Rule findProxyRule(Exchange exc) {
		for (Rule rule : router.getRuleManager().getRules()) {
			if (!(rule instanceof ProxyRule))
				continue;

			if (rule.getKey().getPort() == exc.getServerThread().getSourceSocket().getLocalPort()) {
				log.debug("proxy rule found: " + rule);
				return rule;
			}
		}
		log.debug("No rule found for incomming request");
		return new NullRule();
	}

	private void insertXForwardedFor(AbstractExchange exc) {
		exc.getRequest().getHeader().setXForwardedFor(getXForwardedForHeaderValue(exc));
	}

	private String getXForwardedForHeaderValue(AbstractExchange exc) {
		if (getXForwardedFor(exc) != null )
			return getXForwardedFor(exc) + ", " + exc.getSourceIp();
		
		return exc.getSourceIp();
	}

	private String getXForwardedFor(AbstractExchange exc) {
		return exc.getRequest().getHeader().getXForwardedFor();
	}

	@Override
	public String toString() {
		return "RuleMatchingInterceptor";
	}

	public boolean isxForwardedForEnabled() {
		return xForwardedForEnabled;
	}

	public void setxForwardedForEnabled(boolean xForwardedForEnabled) {
		this.xForwardedForEnabled = xForwardedForEnabled;
	}

}
