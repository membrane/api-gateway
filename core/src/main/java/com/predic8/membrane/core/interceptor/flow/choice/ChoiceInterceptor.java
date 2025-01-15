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
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "choose")
public class ChoiceInterceptor extends AbstractFlowInterceptor {

    private final List<Case> cases = new ArrayList<>();
    private Otherwise otherwise;

    @Override
    public void init() {
        cases.forEach(c -> c.init(router));
        interceptors.addAll(otherwise.getInterceptors());
        interceptors.addAll(cases.stream()
                .map(InterceptorContainer::getInterceptors)
                .flatMap(Collection::stream)
                .toList()
        );
        super.init();
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
        Case choice = findTrueCase(exc, flow);

        if (choice == null)
            return invokeInterceptorsAndReturn(otherwise.getInterceptors(), exc, flow);

        return invokeInterceptorsAndReturn(choice.getInterceptors(), exc, flow);
    }

    private @Nullable Case findTrueCase(Exchange exc, Flow flow) {
        Case choice = null;
        for (Case c : cases) {
            if (c.evaluate(exc, flow, router)) {
                choice = c;
                break;
            }
        }
        return choice;
    }

    private Outcome invokeInterceptorsAndReturn(List<Interceptor> interceptors, Exchange exc, Flow flow) {
        try {
            return switch (flow) {
                case REQUEST -> getFlowController().invokeRequestHandlers(exc, interceptors);
                case RESPONSE -> getFlowController().invokeResponseHandlers(exc, interceptors);
                default -> throw new RuntimeException("Should never happen");
            };
        } catch (Exception e) {
            ProblemDetails.internal(router.isProduction())
                .detail("Error invoking plugin: " + e.getLocalizedMessage())
                .component(e.getClass().getSimpleName())
                .buildAndSetResponse(exc);
            throw new ExchangeExpressionException("Error evaluating expression on exchange in if plugin.", e);
        }
    }

    public void setCases(List<Case> cases) {
        this.cases.addAll(cases);
    }

    @MCChildElement(order = 1)
    public void setOtherwise(Otherwise otherwise) {
        this.otherwise = otherwise;
    }

    @MCChildElement(order = 2, allowForeign = true)
    public void setInterceptors(List<Interceptor> interceptors) {
        /*
        We use <case> and <otherwise> child elements to set interceptors, not child interceptors.
        Therefore, we have to overwrite this so interceptors cannot be added through direct child elements.
         */

    }
}
