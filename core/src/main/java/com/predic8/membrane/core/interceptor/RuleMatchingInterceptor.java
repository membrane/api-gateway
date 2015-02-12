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

package com.predic8.membrane.core.interceptor;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.NullRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;

@MCElement(name="ruleMatching")
public class RuleMatchingInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(RuleMatchingInterceptor.class.getName());

	private boolean xForwardedForEnabled = true;
	private int maxXForwardedForHeaders = 20;

	public RuleMatchingInterceptor() {
		name = "Rule Matching Interceptor";		
		setFlow(Flow.Set.REQUEST);
	}
	
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRule() != null ) return Outcome.CONTINUE;
		
		Rule rule = getRule(exc);
		exc.setRule(rule);
		
		if (rule instanceof NullRule) {
			handleNoRuleFound(exc);
			return Outcome.ABORT;
		}
		
		if (xForwardedForEnabled && (rule instanceof AbstractServiceProxy))
			insertXForwardedFor(exc);

		return Outcome.CONTINUE;
	}

	private void handleNoRuleFound(Exchange exc) throws IOException {
		exc.setResponse(
				Response.badRequest(
						"This request was not accepted by " + 
								"<a href=\"http://www.membrane-soa.org/esb-doc/\">" + Constants.PRODUCT_NAME + "</a>" + 
								". Please correct the request and try again.",
						false).build());
	}

	private Rule getRule(Exchange exc) {
		Request request = exc.getRequest();
		AbstractHttpHandler handler = exc.getHandler();
		
		// retrieve value to match
		String hostHeader = request.getHeader().getHost();
		String method = request.getMethod();
		String uri = request.getUri();
		int port = handler.isMatchLocalPort() ? handler.getLocalPort() : -1;
		String localIP = handler.getLocalAddress().getHostAddress();

		// match it
		Rule rule = router.getRuleManager().getMatchingRule(hostHeader, method, uri, port, localIP);
		if (rule != null) {
			if (log.isDebugEnabled())
				log.debug("Matching Rule found for RuleKey " + hostHeader + " " + method + " " + uri + " " + port + " " + localIP);
			return rule;
		}

		return findProxyRule(exc);
	}

	private Rule findProxyRule(Exchange exc) {
		for (Rule rule : router.getRuleManager().getRules()) {
			if (!(rule instanceof ProxyRule))
				continue;

			if (rule.getKey().getIp() != null)
				if (!rule.getKey().getIp().equals(exc.getHandler().getLocalAddress().toString()))
					continue;

			
			if (rule.getKey().getPort() == -1 || exc.getHandler().getLocalPort() == -1 || rule.getKey().getPort() == exc.getHandler().getLocalPort()) {
				if (log.isDebugEnabled())
					log.debug("proxy rule found: " + rule);
				return rule;
			}
		}
		log.debug("No rule found for incoming request");
		return new NullRule();
	}

	private void insertXForwardedFor(AbstractExchange exc) {
		Header h = exc.getRequest().getHeader();
		if (h.getNumberOf(Header.X_FORWARDED_FOR) > maxXForwardedForHeaders) {
			Request r = exc.getRequest();
			throw new RuntimeException("Request caused " + Header.X_FORWARDED_FOR + " flood: " + r.getStartLine() +  
					r.getHeader().toString());
		}
		h.setXForwardedFor(getXForwardedForHeaderValue(exc));
	}

	private String getXForwardedForHeaderValue(AbstractExchange exc) {
		if (getXForwardedFor(exc) != null )
			return getXForwardedFor(exc) + ", " + exc.getRemoteAddrIp();
		
		return exc.getRemoteAddrIp();
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
	
	@MCAttribute
	public void setxForwardedForEnabled(boolean xForwardedForEnabled) {
		this.xForwardedForEnabled = xForwardedForEnabled;
	}
	
	public int getMaxXForwardedForHeaders() {
		return maxXForwardedForHeaders;
	}
	
	@MCAttribute
	public void setMaxXForwardedForHeaders(int maxXForwardedForHeaders) {
		this.maxXForwardedForHeaders = maxXForwardedForHeaders;
	}

}
