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
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.lang.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name = "call")
public class CallInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CallInterceptor.class.getName());

    private static HTTPClientInterceptor hcInterceptor;

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
        hcInterceptor = new HTTPClientInterceptor();
        hcInterceptor.init(router);
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
        List<String> oldDest = exc.getDestinations();
        Outcome outcome = doCall(exc);
        exc.setDestinations(oldDest);
        return outcome;
    }

    private @NotNull Outcome doCall(Exchange exc) {
        try {
            exc.setDestinations(List.of(exchangeExpression.evaluate(exc, REQUEST, String.class)));
        } catch (ExchangeExpressionException e) {
            e.provideDetails(internal(getRouter().isProduction(),getDisplayName())).buildAndSetResponse(exc);
            return ABORT;
        }
        log.debug("Calling {}", exc.getDestinations());
        try {
            Outcome outcome = hcInterceptor.handleRequest(exc);
            if (outcome == ABORT) {
                log.warn("Aborting. Error calling {}", exc.getDestinations());
                return ABORT;
            }
            exc.getRequest().setBodyContent(exc.getResponse().getBody().getContent()); // TODO Optimize?
            copyHeadersFromResponseToRequest(exc);
            exc.getRequest().getHeader().setContentType(exc.getResponse().getHeader().getContentType());
            log.debug("Outcome of call {}", outcome);
            return CONTINUE;
        } catch (Exception e) {
            log.error("",e);
            internal(router.isProduction(),getDisplayName())
                    .detail("Internal call")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    static void copyHeadersFromResponseToRequest(Exchange exc) {
        Arrays.stream(exc.getResponse().getHeader().getAllHeaderFields()).forEach(headerField -> {
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
