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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.proxies.*;
import org.slf4j.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@SuppressWarnings("unused")
@MCElement(name="ruleMatching")
public class RuleMatchingInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RuleMatchingInterceptor.class.getName());

	private boolean xForwardedForEnabled = true;
	private int maxXForwardedForHeaders = 20;

	public RuleMatchingInterceptor() {
		name = "rule matching interceptor";
		setFlow(REQUEST_FLOW);
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		if (exc.getProxy() != null ) return CONTINUE;

		Proxy proxy = getRule(exc);
		assignRule(exc, proxy);

		if (proxy instanceof NullProxy) {
			// Do not log. 404 is too common
            user(false,"routing")
                    .statusCode(404)
                    .title("Wrong path or method")
                    .detail("This request was not accepted by Membrane. Please check HTTP method and path.")
                    .topLevel("method", exc.getRequest().getMethod())
                    .topLevel("uri", exc.getRequest().getUri()).buildAndSetResponse(exc);
			return ABORT;
		}

		if (xForwardedForEnabled && (proxy instanceof AbstractServiceProxy))
			insertXForwardedFor(exc);

		return CONTINUE;
	}

	public static void assignRule(Exchange exc, Proxy proxy) {
		exc.setProxy(proxy);
		if (!(proxy instanceof SSLableProxy sp))
			return;

		if(sp.isOutboundSSL()){
			exc.setProperty(SSL_CONTEXT, sp.getSslOutboundContext());
		}
	}

	private Proxy getRule(Exchange exc) {
		return router.getRuleManager().getMatchingRule(exc);
	}

	private void insertXForwardedFor(AbstractExchange exc) {
		Header h = exc.getRequest().getHeader();
		if (h.getNumberOf(X_FORWARDED_FOR) > maxXForwardedForHeaders) {
			Request r = exc.getRequest();
			throw new RuntimeException("Request caused X-Forwarded-For flood: " + r.getStartLine() + r.getHeader());
		}
		h.setXForwardedFor(getXForwardedForHeaderValue(exc));

		if (h.getNumberOf(Header.X_FORWARDED_PROTO) > maxXForwardedForHeaders) {
			Request r = exc.getRequest();
			throw new RuntimeException("Request caused " + Header.X_FORWARDED_PROTO + " flood: " + r.getStartLine() +
					r.getHeader().toString());
		}
		h.setXForwardedProto(getXForwardedProtoHeaderValue(exc));


		if (h.getNumberOf(Header.X_FORWARDED_HOST) > maxXForwardedForHeaders) {
			Request r = exc.getRequest();
			throw new RuntimeException("Request caused " + Header.X_FORWARDED_HOST + " flood: " + r.getStartLine() +
					r.getHeader().toString());
		}
		h.setXForwardedHost(getXForwardedHostHeaderValue(exc));

	}

	private String getXForwardedHostHeaderValue(AbstractExchange exc) {
		if(getXForwardedHost(exc) != null)
			return getXForwardedHost(exc) + ", " + exc.getRequest().getHeader().getHost();
		return exc.getRequest().getHeader().getHost();
	}

	private String getXForwardedHost(AbstractExchange exc) {
		return exc.getRequest().getHeader().getXForwardedHost();
	}

	private String getXForwardedForHeaderValue(AbstractExchange exc) {
		if (getXForwardedFor(exc) != null )
			return getXForwardedFor(exc) + ", " + exc.getRemoteAddrIp();

		return exc.getRemoteAddrIp();
	}

	private String getXForwardedProtoHeaderValue(AbstractExchange exc) {
		if (getXForwardedProto(exc) != null )
			return getXForwardedProto(exc);

		return exc.getProxy().getProtocol();
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

	@SuppressWarnings("unused")
	public boolean isxForwardedForEnabled() {
		return xForwardedForEnabled;
	}

	@MCAttribute
	public void setxForwardedForEnabled(boolean xForwardedForEnabled) {
		this.xForwardedForEnabled = xForwardedForEnabled;
	}

	@SuppressWarnings("unused")
	public int getMaxXForwardedForHeaders() {
		return maxXForwardedForHeaders;
	}

	@MCAttribute
	public void setMaxXForwardedForHeaders(int maxXForwardedForHeaders) {
		this.maxXForwardedForHeaders = maxXForwardedForHeaders;
	}

}
