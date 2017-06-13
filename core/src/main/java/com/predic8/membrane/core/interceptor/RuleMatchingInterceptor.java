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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class RuleMatchingInterceptor extends AbstractInterceptor {

	private static Logger log = LoggerFactory.getLogger(RuleMatchingInterceptor.class.getName());

	private boolean xForwardedForEnabled = true;
	private int maxXForwardedForHeaders = 20;

	public RuleMatchingInterceptor() {
		name = "Rule Matching Interceptor";
		setFlow(Flow.Set.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRule() != null ) return Outcome.CONTINUE;

		Rule rule = getRule(exc);
		exc.setRule(rule);
		if(exc.getRule().getSslOutboundContext() != null){
			exc.setProperty(Exchange.SSL_CONTEXT, exc.getRule().getSslOutboundContext());
		}

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
								"<a href=\"" + Constants.PRODUCT_WEBSITE_DOC + "\">" + Constants.PRODUCT_NAME + "</a>" +
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
		String version = request.getVersion();
		int port = handler.isMatchLocalPort() ? handler.getLocalPort() : -1;
		String localIP = handler.getLocalAddress().getHostAddress();

		// match it
		Rule rule = router.getRuleManager().getMatchingRule(hostHeader, method, uri, version, port, localIP);
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

		if (h.getNumberOf(Header.X_FORWARDED_PROTO) > maxXForwardedForHeaders) {
			Request r = exc.getRequest();
			throw new RuntimeException("Request caused " + Header.X_FORWARDED_PROTO + " flood: " + r.getStartLine() +
					r.getHeader().toString());
		}
		h.setXForwardedProto(getXForwardedProtoHeaderValue(exc));

	}

	private String getXForwardedForHeaderValue(AbstractExchange exc) {
		if (getXForwardedFor(exc) != null )
			return getXForwardedFor(exc) + ", " + exc.getRemoteAddrIp();

		return exc.getRemoteAddrIp();
	}

	private String getXForwardedProtoHeaderValue(AbstractExchange exc) {
		String proto = ((Exchange)exc).getRule().getSslInboundContext() != null ? "https" : "http";
		if (getXForwardedProto(exc) != null )
			return getXForwardedProto(exc);

		return proto;
	}

	private String getXForwardedFor(AbstractExchange exc) {
		return exc.getRequest().getHeader().getXForwardedFor();
	}

	private String getXForwardedProto(AbstractExchange exc) {
		return exc.getRequest().getHeader().getXForwardedProto();
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

	public int getMaxXForwardedForHeaders() {
		return maxXForwardedForHeaders;
	}

	
	public void setMaxXForwardedForHeaders(int maxXForwardedForHeaders) {
		this.maxXForwardedForHeaders = maxXForwardedForHeaders;
	}

}
