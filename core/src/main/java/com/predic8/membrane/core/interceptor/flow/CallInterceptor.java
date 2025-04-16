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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.IOException;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.Collections.singletonList;

@MCElement(name = "call")
public class CallInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CallInterceptor.class.getName());


    /**
     * These headers are filtered out from the response of a called resource
     * and are not added to the current message.
     */
    private static final List<String> REMOVE_HEADERS = List.of(
            SERVER, TRANSFER_ENCODING, CONTENT_ENCODING
    );

    /**
     * These headers are considered relevant for a call
     * and will be copied from the original exchange if present.
     */
    private static final List<String> ALLOWED_REQUEST_HEADERS = Arrays.asList(
            AUTHORIZATION, ACCEPT, CONTENT_TYPE, COOKIE, USER_AGENT, ORIGIN
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
        String dest = exchangeExpression.evaluate(exc, REQUEST, String.class);
        log.debug("Calling {}", dest);

        Exchange newExc = getNewExchange(dest, getNewRequest(exc));

        try(HttpClient client = new HttpClient()) {
            client.call(newExc);
        } catch (Exception e) {
            return ABORT;
        }

        try {
            exc.getRequest().setBodyContent(newExc.getResponse().getBody().getContent());
            copyHeadersFromResponseToRequest(newExc, exc);
            return CONTINUE;
        } catch (Exception e) {
            log.error("",e);
            internal(router.isProduction(),getDisplayName())
                    .addSubSee("internal-calling")
                    .detail("Internal call")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private Request getNewRequest(Exchange exc) {
        Request request = new Request.Builder()
                .method(exc.getRequest().getMethod())
                .header(getRequestHeader(exc))
                .build();
        setRequestBody(request, exc);
        return request;
    }

    private static @NotNull Exchange getNewExchange(String dest, Request request) {
        Exchange newExc = new Exchange(null);
        newExc.setDestinations(singletonList(dest));
        newExc.setRequest(request);
        return newExc;
    }

    private void setRequestBody(Request request, Exchange exc) {
        if (!methodShouldHaveBody(exc.getRequest().getMethod()))
            return;
        try {
            request.setBodyContent(exc.getRequest().getBody().getContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean methodShouldHaveBody(String method) {
        return method.equals("POST") ||  method.equals("PUT") || method.equals("PATCH");
    }

    private Header getRequestHeader(Exchange exc) {
        Header requestHeader = new Header();
        for (HeaderField field : exc.getRequest().getHeader().getAllHeaderFields()) {
            if (ALLOWED_REQUEST_HEADERS.stream().anyMatch(h -> h.equalsIgnoreCase(field.getHeaderName().getName()))) {
                requestHeader.add(field);
            }
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
