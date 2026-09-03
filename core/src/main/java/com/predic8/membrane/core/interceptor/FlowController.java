/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.router.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * Controls the flow of an exchange through a chain of interceptors. What the outcomes mean and
 * when an interceptor should return which is documented on {@link Interceptor} and
 * {@link Outcome}.
 *
 * In the trivial setup, an exchange passes through two chains until it hits RETURN: the main
 * chain owned by the Transport (rule matching, dispatching, the UserFeatureInterceptor and the
 * HTTP client, among others) and the inner chain owned by the UserFeatureInterceptor, holding
 * the interceptors configured for the matched proxy.
 *
 * The {@link HTTPClientInterceptor}, the last interceptor in the main chain, always returns
 * {@link Outcome#RETURN} or {@link Outcome#ABORT}, never {@link Outcome#CONTINUE}.
 *
 * A chain is followed calling {@link Interceptor#handleRequest(Exchange)} on every interceptor
 * that {@link Interceptor#handlesRequests()}, until one of them does not return
 * {@link Outcome#CONTINUE}. The position it stopped at is the index the flow is reversed from.
 *
 * When {@link Outcome#RETURN} is hit, the chain is walked backwards from that position, calling
 * {@link Interceptor#handleResponse(Exchange)} on the interceptors before it.
 *
 * When {@link Outcome#ABORT} is hit, or an interceptor throws, the chain is walked backwards the
 * same way, calling {@link Interceptor#handleAbort(Exchange)} instead. An exception is turned
 * into an error response first and is kept in the exchange property {@link #ABORTION_REASON}.
 */
public class FlowController {

    private static final Logger log = LoggerFactory.getLogger(FlowController.class);

    private final Router router;

    public FlowController(Router router) {
        this.router = router;
    }

    // TODO Still needed check
    public static final String ABORTION_REASON = "abortionReason";

    /**
     * Runs the request handlers of the given chain. If an interceptor returns
     * RETURN or ABORT the flow is reversed and the method runs the response or
     * abort flow back.
     */
    public Outcome invokeRequestHandlers(Exchange exchange, List<Interceptor> interceptors) {
        Flow flow = REQUEST;
        for (int i = 0; i < interceptors.size(); i++) {
            Interceptor interceptor = interceptors.get(i);
            if (!interceptor.handlesRequests())
                continue;

            try {
                Outcome o = interceptor.handleRequest(exchange);
                if (o == RETURN) {
                    log.debug("Interceptor returned RETURN. Returning!");
                    flow = RESPONSE;
                    invokeResponseHandlers(exchange, interceptors, i);
                    return RETURN;
                }
                if (o == ABORT) {
                    log.debug("Interceptor returned ABORT. Aborting!");
                    flow = Flow.ABORT;
                    invokeAbortHandlers(exchange, interceptors, i);
                    return ABORT;
                }
            } catch (Exception e) {
                String msg = "Aborting! Exception caused in %s during %s %s flow.".formatted(interceptor.getDisplayName(), exchange.getRequest().getUri(), flow);
                log.warn(msg, e);
                internal(router.getConfiguration().isProduction(),interceptor.getDisplayName())
                        .detail(msg)
                        .exception(e)
                        .buildAndSetResponse(exchange);
                exchange.setProperty(ABORTION_REASON, e);
                invokeAbortHandlers(exchange, interceptors, i);
                return ABORT;
            }
        }
        return CONTINUE;
    }

    public Outcome invokeResponseHandlers(Exchange exchange, List<Interceptor> interceptors) {
        return invokeResponseHandlers(exchange, interceptors, interceptors.size());
    }

    /**
     * Run interceptors backward from current position.
     *
     * @param exchange Exchange
     * @param interceptors List of all interceptors
     * @param pos Position of called interceptors in the interceptors list
     */
    public Outcome invokeResponseHandlers(Exchange exchange, List<Interceptor> interceptors, int pos) {
        boolean aborted = false;
        for (int i = pos - 1; i >= 0; i--) {
            Interceptor interceptor = interceptors.get(i);
            if (!interceptor.handlesResponses())
                continue;
            if (!aborted) {
                if (interceptor.handleResponse(exchange) == ABORT) {
                    aborted = true;
                }
                continue;
            }
            interceptor.handleAbort(exchange);
        }
        if (aborted)
            return ABORT;
        return CONTINUE;
    }

    public void invokeAbortHandlers(Exchange exchange, List<Interceptor> interceptors) {
        invokeAbortHandlers(exchange, interceptors, interceptors.size());
    }

        /**
         * Run interceptors backward from current position and calls handleAbort
         *
         * @param exchange
         * @param interceptors
         * @param pos          Position of called interceptors in the interceptors list
         */
    public void invokeAbortHandlers(Exchange exchange, List<Interceptor> interceptors, int pos) {
        for (int i = pos - 1; i >= 0; i--) {
            try {
                interceptors.get(i).handleAbort(exchange);
            } catch (Exception e) {
                log.error("Exception handling abort interceptor. Ignoring: Continuing to abort!", e);
            }

        }
    }
}