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
package com.predic8.membrane.core.interceptor.flow.choice;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ExchangeExpressionException;

import java.util.List;

abstract class InterceptorContainer {

    private List<Interceptor> interceptors;

    Outcome invokeFlow(Exchange exc, Flow flow, Router router) {
        try {
            return switch (flow) {
                case REQUEST -> router.getFlowController().invokeRequestHandlers(exc, interceptors);
                case RESPONSE -> router.getFlowController().invokeResponseHandlers(exc, interceptors);
                default -> throw new RuntimeException("Should never happen");
            };
        } catch (Exception e) {
            handleInvocationProblemDetails(exc, e, router);
            throw new ExchangeExpressionException("Error evaluating expression on exchange in if plugin.", e);
        }
    }

    private void handleInvocationProblemDetails(Exchange exc, Exception e, Router router) {
        ProblemDetails.internal(router.isProduction())
            .detail("Error invoking plugin: " + e.getLocalizedMessage())
            .component(e.getClass().getSimpleName())
            .buildAndSetResponse(exc);
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @MCChildElement(allowForeign = true)
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }
}
