/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractExchangeExpressionInterceptor;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.MessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.api.client.http.HttpMethods.*;
import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.TemplateUtil.containsTemplateMarker;
import static java.util.Collections.singletonList;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * @description Calls an external HTTP endpoint and merges its response into the current message: the
 * request in the request flow, the response in the response flow. The header fields of the current
 * message are sent to the endpoint. The endpoint's decoded response body replaces the message body,
 * and its header fields are copied onto the message; the status code of a response is kept. Fields
 * that apply to one connection only are neither sent nor copied: <code>Connection</code> and the
 * fields it names, <code>Keep-Alive</code>, <code>Proxy-Connection</code>, <code>TE</code>,
 * <code>Trailer</code> and <code>Upgrade</code>. <code>Server</code>, <code>Content-Length</code>,
 * <code>Content-Encoding</code> and <code>Transfer-Encoding</code> are not copied either; the message
 * gets a <code>Content-Length</code> for its new body. Placed directly in a flow, it runs in both the
 * request and the response flow. The <code>url</code> may contain <code>${...}</code> expressions that
 * are evaluated against the current message before each call; a URL without a template marker is used
 * verbatim. On an unknown host or a failed call the exchange is aborted with a Problem Details
 * response. See the tutorials under tutorials/orchestration.
 * @topic 1. Proxies and Flow
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - request:
 *         - call:
 *             url: https://api.predic8.de/shop/v2/products
 *     - return:
 *         status: 200
 * </code></pre>
 */
@MCElement(name = "call")
public class CallInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CallInterceptor.class);

    /**
     * If url contains template marker ${}, if not expression evaluation is skipped
     */
    private boolean urlIsTemplate = false;

    /**
     * These headers are filtered out from the response of a called resource
     * and are not added to the current message.
     */
    private static final List<String> REMOVE_HEADERS = List.of(
            SERVER, CONTENT_LENGTH, CONTENT_ENCODING, TRANSFER_ENCODING
    );

    /**
     * Fields that apply to one connection only, and are neither sent to the called resource nor copied from its
     * response: the hop-by-hop fields of RFC 9110, section 7.6.1, and <code>Trailer</code>, which announces the
     * trailer fields of a chunked body. Bodies are sent and set with a framing of their own. The fields the
     * response's <code>Connection</code> header names are not copied either.
     */
    private static final List<String> HOP_BY_HOP_HEADERS = List.of(
            CONNECTION, KEEP_ALIVE, PROXY_CONNECTION, "TE", "Trailer", UPGRADE
    );

    private static final Set<String> FIXED_HOP_BY_HOP_HEADERS = HOP_BY_HOP_HEADERS.stream()
            .map(name -> name.toLowerCase(ROOT))
            .collect(toUnmodifiableSet());

    private String method = GET;

    /**
     * Optional per-element config; the router's global config applies if null.
     */
    private HttpClientConfiguration httpClientConfig;

    /**
     * Shared client created from the router's httpClientConfig; written in init(), read-only afterwards.
     */
    private HttpClient httpClient;

    @Override
    public void init() {
        super.init();

        httpClient = router.getHttpClientFactory().createClient(httpClientConfig != null ? httpClientConfig : router.getHttpClientConfig());

        if (router.getConfiguration().getUriFactory().isAllowIllegalCharacters()) {
             throw new ConfigurationException("""
                    Illegal URL Characters

                    The call interceptor cannot be used while illegal characters in URLs are enabled. Disable them in
                    the configuration (configuration/uriFactory/allowIllegalCharacters) to use <call>. Affected call
                    URL: %s
                    """.formatted(exchangeExpression.getExpression()));
        }

        // If there is no template marker ${ than do not try to evaluate url as expression
        if (containsTemplateMarker(exchangeExpression.getExpression())) {
            urlIsTemplate = true;
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        final var message = exc.getMessage(flow);
        var dest = computeDestinationUrl(exc, flow);
        log.debug("Calling {}", dest);

        final Exchange newExc = createNewExchange(dest, getNewRequest(message));

        try {
            httpClient.call(newExc);
        } catch (UnknownHostException e) {
            log.error("Error calling: {} Unknown host: {}", dest, e.getMessage());
            createProblemDetails(dest)
                    .detail("Unknown host " + e.getMessage())
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (Exception e) {
            log.error("Error during HTTP call to {}: {}", dest, e.getMessage(), e);
            createProblemDetails(dest).buildAndSetResponse(exc);
            return ABORT;
        }

        try {
            /**
             * The content is copied from the response with decoding. The response headers transfer-encoding
             * and content-encoding are removed by the setBodyContent method.
             */
            try (final var body = newExc.getResponse().getBodyAsStreamDecoded()) {
                message.setBodyContent(body.readAllBytes());
            }
            copyHeaders(newExc.getResponse(), message);
            return CONTINUE;
        } catch (Exception e) {
            log.error("Error processing response from {}: {}", dest, e.getMessage(), e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .addSubSee("internal-calling")
                    .detail("Internal call")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private String computeDestinationUrl(Exchange exc, Flow flow) {
        if (urlIsTemplate) {
            return exchangeExpression.evaluate(exc, flow, String.class);
        }
        return exchangeExpression.getExpression();
    }

    private ProblemDetails createProblemDetails(String dest) {
        return internal(router.getConfiguration().isProduction(), "call")
                .title("Error performing callout.")
                .internal("expression", exchangeExpression.getExpression())
                .internal("destination", dest);
    }

    private Request getNewRequest(Message message) {
        Request.Builder builder = new Request.Builder()
                .method(method)
                .header(getFilteredRequestHeader(message));
        setRequestBody(builder, message);
        return builder.build();
    }

    private static Exchange createNewExchange(String dest, Request request) {
        Exchange exc = new Exchange(null);
        exc.setDestinations(singletonList(dest));
        exc.setRequest(request);
        return exc;
    }

    /**
     * Sends the body decoded: <code>Request.Builder.body()</code> removes <code>Content-Encoding</code>.
     */
    private void setRequestBody(Request.Builder builder, Message message) {
        if (!methodShouldHaveBody(method)) {
            return;
        }
        builder.body(MessageUtil.getContent(message));
    }

    private static boolean methodShouldHaveBody(String method) {
        return method.equals(POST) || method.equals(PUT) || method.equals(PATCH);
    }

    /**
     * Filters and returns the request headers relevant for the outgoing request.
     * Only the fixed hop-by-hop fields are left out, not the fields the message's <code>Connection</code> header
     * names: by now the gateway may have added fields of its own, such as <code>X-Forwarded-For</code> or
     * credentials, and the client must not be able to remove them by naming them.
     */
    Header getFilteredRequestHeader(Message message) {
        Header requestHeader = new Header();
        for (HeaderField field : message.getHeader().getAllHeaderFields()) {
            if (isHopByHop(field, FIXED_HOP_BY_HOP_HEADERS))
                continue;
            // Not using a reference on purpose
            requestHeader.add(field.getHeaderName().getName(), field.getValue());
        }
        // Removes body-related headers when no body is present
        if (!methodShouldHaveBody(method)) {
            requestHeader.removeFields(CONTENT_TYPE);
            requestHeader.removeFields(CONTENT_LENGTH);
            requestHeader.removeFields(TRANSFER_ENCODING);
            requestHeader.removeFields(CONTENT_LENGTH);
        }
        return requestHeader;
    }

    /**
     * Copies the header fields of the called resource's response onto the current message.
     */
    static void copyHeaders(final Response source, final Message target) {
        final var hopByHopHeaders = getHopByHopHeaders(source.getHeader());
        Arrays.stream(source.getHeader().getAllHeaderFields()).forEach(headerField -> {
            // Filter out, what is definitely not needed like Server:
            for (String rmHeader : REMOVE_HEADERS) {
                if (headerField.getHeaderName().getName().equalsIgnoreCase(rmHeader))
                    return;
            }
            if (isHopByHop(headerField, hopByHopHeaders))
                return;
            target.getHeader().setValue(headerField.getHeaderName().getName(), headerField.getValue());
        });
    }

    /**
     * Lower-case names of the fixed hop-by-hop fields and of the fields the <code>Connection</code> header names.
     */
    private static Set<String> getHopByHopHeaders(final Header header) {
        return Stream.concat(HOP_BY_HOP_HEADERS.stream(), header.getSingleValues(CONNECTION))
                .map(name -> name.toLowerCase(ROOT))
                .collect(toUnmodifiableSet());
    }

    private static boolean isHopByHop(final HeaderField field, final Set<String> hopByHopHeaders) {
        return hopByHopHeaders.contains(field.getHeaderName().getName().toLowerCase(ROOT));
    }

    /**
     * @description Target URL of the call. May contain <code>${...}</code> expressions that are
     * evaluated against the exchange before each call; without a template marker the value is used
     * verbatim. This interceptor cannot be used when <code>allowIllegalCharacters</code> is enabled on
     * the URI factory.
     * @example https://api.predic8.de/shop/v2/products
     */
    @SuppressWarnings("unused")
    @MCAttribute
    @Required
    public void setUrl(String url) {
        this.expression = url;
    }

    public String getUrl() {
        return expression;
    }

    /**
     * @description HTTP method used for the call. With <code>POST</code>, <code>PUT</code> or
     * <code>PATCH</code> the current message body is sent, decoded; other methods are sent without a body.
     * @default GET
     * @example POST
     */
    @MCAttribute
    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public HttpClientConfiguration getHttpClientConfig() {
        return httpClientConfig;
    }

    /**
     * @description Connection pooling, timeout, retry, and proxy settings for outgoing HTTP connections made by
     * this element. When omitted, the router's global <code>httpClientConfig</code> applies.
     */
    @MCChildElement
    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }

    @Override
    public String getDisplayName() {
        return "call";
    }

    @Override
    public String getShortDescription() {
        return "Calls %s".formatted(expression);
    }
}
