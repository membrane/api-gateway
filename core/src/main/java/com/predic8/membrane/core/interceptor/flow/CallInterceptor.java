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
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractExchangeExpressionInterceptor;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.Collections.singletonList;

@MCElement(name = "call")
public class CallInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CallInterceptor.class);

    /**
     * These headers are filtered out from the response of a called resource
     * and are not added to the current message.
     */
    private static final List<String> REMOVE_HEADERS = List.of(
            SERVER, TRANSFER_ENCODING, CONTENT_ENCODING
    );

    @Override
    public void init() {
        super.init();
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
        final String dest = exchangeExpression.evaluate(exc, REQUEST, String.class);
        log.debug("Calling {}", dest);

        final Exchange newExc = createNewExchange(dest, getNewRequest(exc));

        try (HttpClient client = new HttpClient()) {
            client.call(newExc);
        } catch (Exception e) {
            log.error("Error during HTTP call to {}: {}", dest, e.getMessage(), e);
            return ABORT;
        }

        try {
            exc.getRequest().setBodyContent(newExc.getResponse().getBody().getContent());
            copyHeadersFromResponseToRequest(newExc, exc);
            return CONTINUE;
        } catch (Exception e) {
            log.error("Error processing response from {}: {}", dest, e.getMessage(), e);
            internal(router.isProduction(), getDisplayName())
                    .addSubSee("internal-calling")
                    .detail("Internal call")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private Request getNewRequest(Exchange exchange) {
        Request.Builder builder = new Request.Builder()
                .method(exchange.getRequest().getMethod())
                .header(getFilteredRequestHeader(exchange));
        setRequestBody(builder, exchange);
        return builder.build();
    }

    private static Exchange createNewExchange(String dest, Request request) {
        Exchange newExc = new Exchange(null);
        newExc.setDestinations(singletonList(dest));
        newExc.setRequest(request);
        return newExc;
    }

    private static void setRequestBody(Request.Builder builder, Exchange exchange) {
        if (!methodShouldHaveBody(exchange.getRequest().getMethod())) {
            return;
        }
        try {
            builder.body(exchange.getRequest().getBody().getContent());
        } catch (IOException e) {
            throw new RuntimeException("Error setting request body", e);
        }
    }

    private static boolean methodShouldHaveBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    /**
     * Filters and returns the request headers relevant for the outgoing request.
     */
    static Header getFilteredRequestHeader(Exchange exc) {
        Header requestHeader = new Header();
        for (HeaderField field : exc.getRequest().getHeader().getAllHeaderFields()) {
            requestHeader.add(field.getHeaderName().getName(), field.getValue());
        }
        // Removes body-related headers when no body is present
        if(!methodShouldHaveBody(exc.getRequest().getMethod())) {
            requestHeader.removeFields(CONTENT_TYPE);
            requestHeader.removeFields(CONTENT_LENGTH);
            requestHeader.removeFields(TRANSFER_ENCODING);
            requestHeader.removeFields(CONTENT_LENGTH);
        }
        return requestHeader;
    }

    static void copyHeadersFromResponseToRequest(Exchange newExc, Exchange exc) {
        Arrays.stream(newExc.getResponse().getHeader().getAllHeaderFields()).forEach(headerField -> {
            // Filter out, what is definitely not needed like Server:
            for (String rmHeader : REMOVE_HEADERS) {
                if (headerField.getHeaderName().getName().equalsIgnoreCase(rmHeader))
                    return;
            }
            exc.getRequest().getHeader().add(headerField);
        });
    }

    /**
     * @default com.predic8.membrane.core.interceptor.log.LogInterceptor
     * @description Sets the category of the logged message.
     * @example Membrane
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

    @Override
    public String getDisplayName() {
        return "call";
    }

    @Override
    public String getShortDescription() {
        return "Calls %s".formatted(expression);
    }
}
