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
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_RESPONSE_ABORT_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;

/**
 * @description Enables conditional branching.
 * Evaluates {@link Case} elements in order and runs the first matching flow.
 * If no case matches, an optional trailing {@link Otherwise} is executed.
 * The "otherwise" element must be the last element of the list.
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - choose:
 *         - case:
 *             test: headers['X-Foo'] != null
 *             flow:
 *               - return:
 *                   status: 200
 *         - case:
 *             test: headers['X-Bar'] != null
 *             flow:
 *               - return:
 *                   status: 300
 *         - otherwise:
 *             - return:
 *                 status: 400
 *
 * </code></pre>
 */
@MCElement(name = "choose", noEnvelope = true)
public class ChooseInterceptor extends AbstractFlowInterceptor {

    private List<AbstractCaseOtherwise> choices = new ArrayList<>();

    private final List<Case> cases = new ArrayList<>();
    private Otherwise otherwise;

    @Override
    public void init() {
        validateChoices(choices);
        setChoices();

        cases.forEach(c -> c.init(router));
        interceptors.addAll(concat(
            otherwise != null ? otherwise.getFlow().stream() : empty(),
            cases.stream()
                .map(InterceptorContainer::getFlow)
                .flatMap(Collection::stream)
        ).toList());
        // Has to be called after adding interceptors.
        super.init();
    }

    public ChooseInterceptor() {
        this.name = "choose";
        this.setAppliedFlow(REQUEST_RESPONSE_ABORT_FLOW);
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

        // Don't inline it! Exception must fly
        Case matchingCase;
        try {
            matchingCase = findTrueCase(exc, flow);
        } catch (ExchangeExpressionException e) {
            handleExpressionProblemDetails(e, exc);
            return ABORT;
        }

        return Optional.ofNullable(matchingCase)
                .map(choice -> choice.invokeFlow(exc, flow, router))
                .orElseGet(() -> otherwise != null ? otherwise.invokeFlow(exc, flow, router) : CONTINUE);
    }

    private @Nullable Case findTrueCase(Exchange exc, Flow flow) {
        for (Case c : cases) {
            if (c.evaluate(exc, flow)) return c;
        }
        return null;
    }

    static void validateChoices(List<AbstractCaseOtherwise> choices) {
        for (int i = 0; i < choices.size(); i++) {
            AbstractCaseOtherwise c = choices.get(i);

            if (c instanceof Otherwise) {
                if (i != choices.size() - 1) {
                    throw new ConfigurationException("'otherwise' must be the last element in 'choose'.");
                }
            }
        }
    }

    private void setChoices() {
        for (AbstractCaseOtherwise c : this.choices) {
            if (c instanceof Case cc) {
                cases.add(cc);
            } else if (c instanceof Otherwise o) {
                otherwise = o;
            }
        }
    }

    private void handleExpressionProblemDetails(ExchangeExpressionException e, Exchange exc) {
        e.provideDetails(internal(router.getConfiguration().isProduction(),getDisplayName()))
            .addSubSee("expression-evaluation")
            .detail("Error evaluating expression on exchange in choose plugin.")
            .buildAndSetResponse(exc);
    }

    /**
     * @description Sets the list of choices. The choices can include "case" and "otherwise" elements to define conditional flows.
     *
     * @param choices the list of choices, which can include instances of {@link Case} for conditional logic
     *                and an optional {@link Otherwise} to specify the default behavior. The "otherwise"
     *                element must be the last in the list if provided.
     */
    @MCChildElement
    public void setChoices(List<AbstractCaseOtherwise> choices) {
        this.choices = choices;
    }

    public List<AbstractCaseOtherwise> getChoices() {
        return choices;
    }

}
