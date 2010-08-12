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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.NullRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.HttpUtil;

public class RuleMatchingInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(RuleMatchingInterceptor.class.getName());

	private boolean xForwardedForEnabled = true;

	public Outcome handleRequest(Exchange aExc) throws Exception {
		if (!(aExc instanceof HttpExchange))
			throw new RuntimeException("RuleMatchingInterceptor accepts only HttpExchange objects");

		HttpExchange exc = (HttpExchange) aExc;

		Rule rule = getRule(exc);
		exc.setRule(rule);
		
		if (rule instanceof NullRule) {
			handleNoRuleFound(exc);
			return Outcome.ABORT;
		}
		
		adjustHostHeader(exc);

		if (xForwardedForEnabled && (rule instanceof ForwardingRule))
			insertXForwardedFor(exc);

		return Outcome.CONTINUE;
	}

	private void handleNoRuleFound(HttpExchange exc) throws IOException {
		exc.getRequest().readBody();
		exc.getServerThread().getSourceSocket().shutdownInput();
		Response res = HttpUtil.createErrorResponse("This request was not accepted by Membrane Monitor. Please correct the request and try again.");
		exc.setResponse(res);
		res.write(exc.getServerThread().getSrcOut());
		exc.getServerThread().getSrcOut().flush();

		exc.setTimeResSent(System.currentTimeMillis());
		exc.finishExchange(true, exc.getErrorMessage());
	}

	private Rule getRule(HttpExchange exc) {
		ForwardingRuleKey key = new ForwardingRuleKey(exc.getRequest().getHeader().getHost(), exc.getRequest().getMethod(), exc.getRequest().getUri(), ((HttpExchange) exc).getServerThread().getSourceSocket().getLocalPort());
		Rule rule = router.getRuleManager().getMatchingRule(key);
		if (rule != null) {
			log.debug("Matching Rule found for RuleKey " + key);
			return rule;
		}

		return findProxyRule(exc);
	}

	private Rule findProxyRule(HttpExchange exc) {
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

	private void insertXForwardedFor(Exchange exc) {
		String value = getXForwardedFor(exc) != null ? getXForwardedFor(exc) + ", " + exc.getSourceIp(): exc.getSourceIp();
		exc.getRequest().getHeader().setXForwardedFor(value);
	}

	private String getXForwardedFor(Exchange exc) {
		return exc.getRequest().getHeader().getXForwardedFor();
	}

	private void adjustHostHeader(Exchange exc) {
		if (exc.getRule() instanceof ForwardingRule) {
			exc.getRequest().getHeader().setHost(((ForwardingRule) exc.getRule()).getTargetHost() + ":" + ((ForwardingRule) exc.getRule()).getTargetPort());
		}
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
