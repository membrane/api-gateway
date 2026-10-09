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
import static com.predic8.membrane.core.util.URIUtil.containsAmbiguousPathParameters;
import static com.predic8.membrane.core.util.URIUtil.normalizeRequestTarget;
import static com.predic8.membrane.core.util.URLUtil.getPathQuery;

@SuppressWarnings("unused")
@MCElement(name="ruleMatching")
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
		try {
			request.setUri(normalizeRequestTarget(toOriginForm(received)));
		} catch (URISyntaxException | IllegalArgumentException e) {
			// A forward proxy passes the absolute URI on unchanged and does not route by its path, so it need not parse
			if (isAbsoluteURI(received) && getRule(exc) instanceof ProxyRule forwardProxy) {
				assignRule(exc, forwardProxy);
				return CONTINUE;
			}
			log.info("Rejected request with malformed request target: {} ({})", received, e.getMessage());
			user(router.isProduction(), "invalid-path")
					.status(400)
					.title("Invalid request target")
					.detail("The request target could not be parsed.")
					.buildAndSetResponse(exc);
			return ABORT;
		}

		if (!isSupportedRequestTarget(request)) {
			log.info("Rejected request with unsupported request target: {}", received);
			user(router.isProduction(), "invalid-path")
					.status(400)
					.title("Invalid request target")
					.detail("The request target must be a path starting with '/' or an absolute URI starting with 'http://' or 'https://'.")
					.buildAndSetResponse(exc);
			return ABORT;
		}

		Proxy proxy = getRule(exc);
		if (proxy instanceof ProxyRule)
			request.setUri(received); // A forward proxy needs the absolute URI
		assignRule(exc, proxy);

		if (request.isCONNECTRequest() && !acceptsConnect(proxy)) {
			log.info("Rejected CONNECT request that is not routed to a forward proxy: {}", received);
			user(router.isProduction(), "invalid-path")
					.status(400)
					.title("Invalid request target")
					.detail("CONNECT is only supported by a forward proxy.")
					.buildAndSetResponse(exc);
			return ABORT;
		}

		if (!(proxy instanceof ProxyRule) && containsAmbiguousPathParameters(request.getUri())) {
			log.info("Rejected request with ambiguous path parameters: {}", request.getUri());
			user(router.isProduction(), "invalid-path")
					.status(400)
					.title("Invalid path")
					.detail("The request path contains path parameters on a dot-segment like '..;' or on an empty segment like '/;'.")
					.buildAndSetResponse(exc);
			return ABORT;
		}

		if (!(proxy instanceof ProxyRule) && hasBackslashInPath(received)) {
			log.info("Rejected request with a backslash in the path: {}", received);
			user(router.isProduction(), "invalid-path")
					.status(400)
					.title("Invalid path")
					.detail("The request path contains a backslash. Send it percent-encoded as %5C.")
					.buildAndSetResponse(exc);
			return ABORT;
		}

		if (proxy instanceof NullProxy) {
			// Do not log. 404 is too common
            user(getRouter().isProduction(), "routing")
                    .status(404)
                    .title("Invalid path or method")
                    .detail("The requested path or HTTP method is not supported.")
					.buildAndSetResponse(exc);
//                    .internal("method", exc.getRequest().getMethod())
//                    .internal("uri", exc.getRequest().getUri()).buildAndSetResponse(exc);
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
	 * Other forms are left as they are.
	 *
	 * @throws URISyntaxException or {@link IllegalArgumentException} if an absolute-form target cannot be parsed
	 */
	private String toOriginForm(String uri) throws URISyntaxException {
		if (!isAbsoluteURI(uri))
			return uri;
		final var parsed = router.getUriFactory().create(uri);
		if (parsed.getHost() == null || parsed.getHost().isBlank())
			throw new URISyntaxException(uri, "Missing host");
		if (parsed.getPort() > 65535)
			throw new URISyntaxException(uri, "Port out of range");
		return getPathQuery(router.getUriFactory(), uri);
	}

	/**
	 * Tells whether the request target has one of the forms of RFC 9112 3.2 that are routed by the same path that is
	 * forwarded: the origin-form <code>/path?q</code>, to which an absolute-form target was already reduced, the
	 * authority-form <code>host:port</code> of CONNECT and the asterisk-form <code>*</code> of OPTIONS.
	 * Other targets like <code>admin</code> or <code>x:/admin</code> are not normalized and do not match the path of
	 * an API, but the DispatchingInterceptor would still forward them as <code>/admin</code>.
	 * A CONNECT passes with any target, since the proxy it is routed to decides, see {@link #acceptsConnect(Proxy)}.
	 */
	private static boolean isSupportedRequestTarget(Request request) {
		final var uri = request.getUri();
		return uri.startsWith("/")
			   || request.isCONNECTRequest()
			   || (request.isOPTIONSRequest() && "*".equals(uri));
	}

	/**
	 * CONNECT asks for a tunnel to the host and port in the request target (RFC 9110 9.3.6), so only a forward proxy
	 * accepts it. An API would open the tunnel as well, and the requests sent through it would bypass routing and the
	 * flow of every API. A STOMP proxy gets the CONNECT frame of STOMP, which does not ask for a tunnel.
	 */
	private static boolean acceptsConnect(Proxy proxy) {
		return proxy instanceof ProxyRule || proxy instanceof STOMPProxy;
	}

	/**
	 * A backslash is not allowed in a URI (RFC 3986), but some servers treat it like "/". Correcting it would let a
	 * crafted request target pass security filters (RFC 9112 3.2), so it is rejected instead.
	 * Checks the target as received, since parsing an absolute-form target escapes a backslash to %5C.
	 * The query is not checked, since browsers send a backslash in the query unencoded.
	 */
	private static boolean hasBackslashInPath(String target) {
		final var queryStart = target.indexOf('?');
		return (queryStart == -1 ? target : target.substring(0, queryStart)).indexOf('\\') != -1;
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
