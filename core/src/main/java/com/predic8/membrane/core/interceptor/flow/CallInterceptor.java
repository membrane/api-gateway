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
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractExchangeExpressionInterceptor;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static com.google.api.client.http.HttpMethods.*;
import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.TemplateUtil.containsTemplateMarker;
import static java.util.Collections.singletonList;

/**
 * @description Calls an external HTTP endpoint and merges its response into the current message. The
 * endpoint's response body replaces the message body, and its headers are copied onto the message
 * except <code>Server</code>, <code>Content-Encoding</code> and <code>Transfer-Encoding</code>. Runs
 * in both the request and the response flow. The <code>url</code> may contain <code>${...}</code>
 * expressions that are evaluated against the exchange before each call; a URL without a template
 * marker is used verbatim. On an unknown host or a failed call the exchange is aborted with a Problem
 * Details response. See the examples and tutorials under examples/orchestration and
 * tutorials/orchestration.
 * @topic 1. Proxies and Flow
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - call:
 *         url: https://api.predic8.de/shop/v2/products
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
            SERVER, CONTENT_ENCODING, TRANSFER_ENCODING
    );

    private String method = GET;

    @Override
    public void init() {
        super.init();

        if (router.getConfiguration().getUriFactory().isAllowIllegalCharacters()) {
             throw new ConfigurationException("""
                    URL Templating and Illegal URL Characters
                    
                    Url templating expressions and enablement of illegal characters in URLs are mutually exclusive. Either disable
                    illegal characters in the configuration (configuration/uriFactory/allowIllegalCharacters) or remove the
                    templating expression %s from the URL in the call URL.
                    """.formatted(exchangeExpression.getExpression()));
        }

        // If there is no template marker ${ than do not try to evaluate url as expression
        if (containsTemplateMarker(exchangeExpression.getExpression())) {
            urlIsTemplate = true;
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc);
    }

    private Outcome handleInternal(Exchange exc) {
        var dest = computeDestinationUrl(exc);
        log.debug("Calling {}", dest);

        final Exchange newExc = createNewExchange(dest, getNewRequest(exc));

        try (HttpClient client = new HttpClient()) {
            client.call(newExc);
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
            exc.getRequest().setBodyContent(newExc.getResponse().getBodyAsStreamDecoded().readAllBytes());
            copyHeadersFromResponseToRequest(newExc, exc);
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

    private String computeDestinationUrl(Exchange exc) {
        if (urlIsTemplate) {
            return exchangeExpression.evaluate(exc, REQUEST, String.class);
        }
        return exchangeExpression.getExpression();
    }

    private ProblemDetails createProblemDetails(String dest) {
        return internal(router.getConfiguration().isProduction(), "call")
                .title("Error performing callout.")
                .internal("expression", exchangeExpression.getExpression())
                .internal("destination", dest);
    }

    private Request getNewRequest(Exchange exchange) {
        Request.Builder builder = new Request.Builder()
                .method(method)
                .header(getFilteredRequestHeader(exchange));
        setRequestBody(builder, exchange);
        return builder.build();
    }

    private static Exchange createNewExchange(String dest, Request request) {
        Exchange exc = new Exchange(null);
        exc.setDestinations(singletonList(dest));
        exc.setRequest(request);
        return exc;
    }

    private void setRequestBody(Request.Builder builder, Exchange exchange) {
        if (!methodShouldHaveBody(method)) {
            return;
        }
        builder.body(exchange.getRequest().getBody().getContent());
    }

    private static boolean methodShouldHaveBody(String method) {
        return method.equals(POST) || method.equals(PUT) || method.equals(PATCH);
    }

    /**
     * Filters and returns the request headers relevant for the outgoing request.
     */
    Header getFilteredRequestHeader(Exchange exc) {
        Header requestHeader = new Header();
        for (HeaderField field : exc.getRequest().getHeader().getAllHeaderFields()) {
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

    static void copyHeadersFromResponseToRequest(Exchange responseExc, Exchange originalExc) {
        Arrays.stream(responseExc.getResponse().getHeader().getAllHeaderFields()).forEach(headerField -> {
            // Filter out, what is definitely not needed like Server:
            for (String rmHeader : REMOVE_HEADERS) {
                if (headerField.getHeaderName().getName().equalsIgnoreCase(rmHeader))
                    return;
            }
            originalExc.getRequest().getHeader().setValue(headerField.getHeaderName().getName(), headerField.getValue());
        });
    }

    /**
     * @description Target URL of the call. May contain <code>${...}</code> expressions that are
     * evaluated against the exchange before each call; without a template marker the value is used
     * verbatim. Templating cannot be combined with <code>allowIllegalCharacters</code> on the URI
     * factory.
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
     * <code>PATCH</code> the current message body is forwarded; other methods are sent without a body.
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

    @Override
    public String getDisplayName() {
        return "call";
    }

    @Override
    public String getShortDescription() {
        return "Calls %s".formatted(expression);
    }
}
