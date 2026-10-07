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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.exchange.Exchange.SSL_CONTEXT;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.HttpUtil.isAbsoluteURI;
import static com.predic8.membrane.core.util.URIUtil.removeDotSegmentsFromRequestTarget;
import static com.predic8.membrane.core.util.URLUtil.getPathQuery;

/**
 * @description Selects and assigns the matching proxy rule for incoming requests; optionally adds/extends X-Forwarded-* headers.
 */
@SuppressWarnings("unused")
@MCElement(name="ruleMatching", excludeFromFlow = true)
public class RuleMatchingInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RuleMatchingInterceptor.class.getName());

	private boolean xForwardedForEnabled = true;
	private int maxXForwardedForHeaders = 20;

	public RuleMatchingInterceptor() {
		name = "rule matching interceptor";
		setAppliedFlow(REQUEST_FLOW);
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		if (exc.getProxy() != null ) return CONTINUE;

		// Canonicalize once, so that routing, the flow and the backend agree on the path
		final var request = exc.getRequest();
		final var received = request.getUri();
		request.setUri(removeDotSegmentsFromRequestTarget(toOriginForm(received)));

		Proxy proxy = getRule(exc);
		if (proxy instanceof ProxyRule)
			request.setUri(received); // A forward proxy needs the absolute URI
		assignRule(exc, proxy);

		if (proxy instanceof NullProxy) {
			// Do not log. 404 is too common
            user(router.getConfiguration().isProduction(), "routing")
                    .status(404)
                    .title("Invalid path or method")
                    .detail("The requested path or HTTP method is not supported.")
					.buildAndSetResponse(exc);
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

	/**
	 * Reduces an absolute-form request target (RFC 9112 3.2.2) like <code>http://host/path?q</code> to the
	 * origin-form <code>/path?q</code> (RFC 9112 3.2.1), so that it is routed by its path.
	 * Other forms and targets that cannot be parsed are left as they are.
	 */
	private String toOriginForm(String uri) {
		if (!isAbsoluteURI(uri))
			return uri;
		try {
			return getPathQuery(router.getConfiguration().getUriFactory(), uri);
		} catch (URISyntaxException e) {
			return uri;
		}
	}

	private Proxy getRule(Exchange exc) {
		return router.getRuleManager().getMatchingRule(exc);
	}

	private void insertXForwardedFor(AbstractExchange exc) {
		Header h = exc.getRequest().getHeader();
		if (h.getNumberOf(X_FORWARDED_FOR) > maxXForwardedForHeaders) {
			throw new RuntimeException(getFloodedErrorMessage(X_FORWARDED_FOR, exc.getRequest()));
		}
		if (h.getNumberOf(X_FORWARDED_PROTO) > maxXForwardedForHeaders) {
            throw new RuntimeException(getFloodedErrorMessage(X_FORWARDED_PROTO, exc.getRequest()));
		}
		if (h.getNumberOf(Header.X_FORWARDED_HOST) > maxXForwardedForHeaders) {
			throw new RuntimeException(getFloodedErrorMessage(X_FORWARDED_HOST, exc.getRequest()));
		}

		h.setXForwardedFor(getXForwardedForHeaderValue(exc));
		h.setXForwardedProto(getXForwardedProtoHeaderValue(exc));
		h.setXForwardedHost(getXForwardedHostHeaderValue(exc));
	}

	private static @NotNull String getFloodedErrorMessage(String header, Request request) {
		return "Request caused %s flood: %s".formatted(header, request.getStartLine() +
															   request.getHeader().toString());
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

	/**
	 * @description Whether <code>X-Forwarded-For</code>, <code>X-Forwarded-Proto</code>, and
	 * <code>X-Forwarded-Host</code> headers are added to requests matched to a service proxy.
	 * @default true
	 */
	@MCAttribute
	public void setxForwardedForEnabled(boolean xForwardedForEnabled) {
		this.xForwardedForEnabled = xForwardedForEnabled;
	}

	@SuppressWarnings("unused")
	public int getMaxXForwardedForHeaders() {
		return maxXForwardedForHeaders;
	}

	/**
	 * @description Maximum number of existing <code>X-Forwarded-For</code>/<code>-Proto</code>/<code>-Host</code>
	 * header occurrences allowed on an incoming request before it is rejected as flooded.
	 * @default 20
	 */
	@MCAttribute
	public void setMaxXForwardedForHeaders(int maxXForwardedForHeaders) {
		this.maxXForwardedForHeaders = maxXForwardedForHeaders;
	}

}
