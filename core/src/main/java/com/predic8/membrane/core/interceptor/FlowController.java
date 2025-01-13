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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.transport.http.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * Controls the flow of an exchange through a chain of interceptors.
 * <p>
 * In the trivial setup, an exchange passes through two chains until it hits
 * RETURN: The main chain owned by the Transport (containing the
 * RuleMatching, Dispatching, UserFeature and HttpClient-Interceptors) and the
 * inner chain owned by the UserFeatureInterceptor (containing any interceptor
 * configured in proxies.xml).
 * <p>
 * The {@link HTTPClientInterceptor}, the last interceptor in the main chain,
 * always returns {@link Outcome#RETURN} or {@link Outcome#ABORT}, never
 * {@link Outcome#CONTINUE}.
 * <p>
 * Any chain is followed using {@link Interceptor#handleRequest(Exchange)} until
 * it hits {@link Outcome#RETURN} or {@link Outcome#ABORT}. As the chain is
 * followed, every interceptor (except those with {@link Flow#REQUEST}) are
 * added to the exchange's stack.
 * <p>
 * When {@link Outcome#RETURN} is hit, the exchange's interceptor stack is
 * unwound and {@link Interceptor#handleResponse(Exchange)} is called for every
 * interceptor on it.
 * <p>
 * When {@link Outcome#ABORT} is hit, handling is aborted: An
 * {@link AbortException} is thrown. The stack is unwound calling
 * {@link Interceptor#handleAbort(Exchange)} on each interceptor on it.
 */
public class FlowController {

    private static final Logger log = LoggerFactory.getLogger(FlowController.class);

    // TODO Still needed check
    public static final String ABORTION_REASON = "abortionReason";

    /**
     * Runs the request handlers of the given chain. If an interceptor returns
     * RETURN or ABORT the flow is reversed and the method runs the response or
     * abort flow back.
     */
    public Outcome invokeRequestHandlers(Exchange exchange, List<Interceptor> interceptors) {

        for (int i = 0; i < interceptors.size(); i++) {
            Interceptor interceptor = interceptors.get(i);
            if (!interceptor.handlesRequests())
                continue;

            try {
                Outcome o = interceptor.handleRequest(exchange);
                if (o == RETURN) {
                    log.debug("Interceptor returned RETURN. Returning!");
                    invokeResponseHandlers(exchange, interceptors, i);
                    return RETURN;
                }
                if (o == ABORT) {
                    log.debug("Interceptor returned ABORT. Aborting!");
                    invokeAbortHandlers(exchange, interceptors, i);
                    return ABORT;
                }
            } catch (Throwable t) {
                log.warn("Exception thrown handling request interceptors. Aborting!", t);
                invokeAbortHandlers(exchange, interceptors, i);
                return ABORT;
            }
        }
        return CONTINUE;
    }

    public Outcome invokeResponseHandlers(Exchange exchange, List<Interceptor> interceptors) throws Exception {
        return invokeResponseHandlers(exchange, interceptors, interceptors.size());
    }

    /**
     * Run interceptors backward from current position.
     *
     * @param exchange Exchange
     * @param interceptors List of all interceptors
     * @param pos Position of called interceptors in the interceptors list
     */
    public Outcome invokeResponseHandlers(Exchange exchange, List<Interceptor> interceptors, int pos) throws Exception {
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