/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.URLUtil.*;

public class InternalRoutingInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InternalRoutingInterceptor.class.getName());
    public static final String REVERSE_INTERCEPTOR_LIST = "membrane.routing.back.interceptors";

    @Override
    public Outcome handleRequest(Exchange exchange) {
        if (!isTargetInternal(exchange))
            return CONTINUE;

        Proxy currentProxy = exchange.getProxy(); // Store current rule (non- "service:..." rule )

        Outcome outcome;
        try {
         outcome = routeService(exchange);
            if (outcome == RETURN) { // An interceptor returned RETURN, so shortcut flow and return
                outcome = getFlowController().invokeResponseHandlers(exchange, getBackInterceptors(exchange));
                if (outcome == CONTINUE) {
                    outcome = RETURN; // Signal that the flow reversed, even when during response flow an interceptor returns CONTINUE
                }
            } else if (outcome == ABORT) { // An interceptor returned ABORT, so shortcut flow and abort
                handleAbort(exchange);
            }
        } catch (Exception e) {
            ProblemDetails.internal(router.isProduction())
                    .component(getDisplayName())
                    .detail("Could not invoke response handler for internal route")
                    .exception(e)
                    .stacktrace(true)
                    .buildAndSetResponse(exchange);
            return Outcome.ABORT;
        }

        exchange.setRule(currentProxy); // Restore current rule, so that the response interceptors of that rule could be invoked
        return outcome;
    }

    @Override
    public void handleAbort(Exchange exc) {
        getFlowController().invokeAbortHandlers(exc, getBackInterceptors(exc));
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        try {
            return getFlowController().invokeResponseHandlers(exc, getBackInterceptors(exc));
        } catch (Exception e) {
            ProblemDetails.internal(router.isProduction())
                    .component(getDisplayName())
                    .detail("Error in response handler chain.")
                    .exception(e)
                    .stacktrace(true)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    /**
     * Route to service.
     *
     * @param exchange
     * @return Outcome
     * @throws Exception
     */
    private @Nullable Outcome routeService(Exchange exchange) throws Exception {
        AbstractServiceProxy service = getRuleByDest(exchange);
        RuleMatchingInterceptor.assignRule(exchange, service);
        updateRequestPath(exchange);
        Outcome outcome = callInterceptors(exchange, service.getInterceptors());
        exchange.getDestinations().clear();
        exchange.getDestinations().add(getTargetAsUri(exchange, service));
        if (outcome == ABORT || outcome == RETURN) {
            return outcome;
        }
        if (isTargetInternal(exchange)) {
            return routeService(exchange); // Service calls service, so continue recursively
        }
        return outcome;
    }

    /**
     * Call the interceptors of the service(s) and store them in a list, so that they can be invoked on the way back again
     *
     * @param exchange
     * @param interceptors
     * @return Outcome
     */
    private static @NotNull Outcome callInterceptors(Exchange exchange, List<Interceptor> interceptors) {

        var backInterceptors = getBackInterceptors(exchange);

        for (Interceptor interceptor : interceptors) {
            if (!interceptor.handlesRequests()) {
                backInterceptors.add(interceptor);
                continue;
            }

            try {
                Outcome o = interceptor.handleRequest(exchange);
                if (o == RETURN || o == ABORT) {
                    return o;
                }
                backInterceptors.add(interceptor);
            } catch (Throwable t) {
                log.debug("Error from interceptor {}. Aborting. Reason {}.", interceptor.getClass().getSimpleName(), t.getMessage());
                return ABORT;
            }
        }
        return CONTINUE;
    }

    private boolean isTargetInternal(Exchange exc) {
        return exc.getDestinations().getFirst().startsWith("internal:");
    }

    private AbstractServiceProxy getRuleByDest(Exchange exchange) {
        Proxy proxy = router.getRuleManager().getRuleByName(getHost(exchange.getDestinations().getFirst()));
        if (proxy == null)
            throw new RuntimeException("No api found for destination " + exchange.getDestinations().getFirst());
        if (proxy instanceof AbstractServiceProxy sp)
            return sp;
        throw new RuntimeException("Not a service proxy: " + proxy.getClass().getSimpleName());
    }

    /**
     * Type List is used to keep it compatible with the functions from FlowController
     *
     * @param exc
     * @return
     */
    private static List<Interceptor> getBackInterceptors(Exchange exc) {
        Object o = exc.getProperty(REVERSE_INTERCEPTOR_LIST);
        if (o != null)
            return (List<Interceptor>) o;

        var l = new ArrayList<Interceptor>();
        exc.setProperty(REVERSE_INTERCEPTOR_LIST, l);
        return l;
    }

    private static void updateRequestPath(Exchange exchange) throws URISyntaxException {
        String path = new URI(exchange.getDestinations().getFirst()).getPath();
        if (path != null && !path.isEmpty()) {
            exchange.getRequest().setUri(path);
        }
    }

    private static @Nullable String getTargetAsUri(Exchange exchange, AbstractServiceProxy service) {
        if (service.getTargetURL() != null) {
            return service.getTargetURL();
        }
        if (service instanceof AbstractServiceProxy asp) {
            if (asp.getTarget().getHost() != null) {
                return service.getTargetScheme() + "://" + asp.getTarget().getHost() + ":" + asp.getTarget().getPort() + exchange.getRequest().getUri();
            }
        }
        return "/";
    }

    @Override
    public String getDisplayName() {
        return "Internal routing";
    }
}