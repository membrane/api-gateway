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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.stream.Stream.*;

/**
 * @description Enables conditional branching.
 * Evaluates {@link Case} elements in order and runs the first matching flow.
 * If no case matches, an optional trailing {@link Otherwise} is executed.
 * The "otherwise" element must be the last element of the list.
 */
@MCElement(name = "choose", noEnvelope = true)
public class ChooseInterceptor extends AbstractFlowInterceptor {

    private List<Choice> choices = new ArrayList<>();

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
        return Optional.ofNullable(findTrueCase(exc, flow))
                .map(choice -> choice.invokeFlow(exc, flow, router))
                .orElseGet(() -> otherwise != null ? otherwise.invokeFlow(exc, flow, router) : CONTINUE);
    }

    private @Nullable Case findTrueCase(Exchange exc, Flow flow) {
        try {
            for (Case c : cases) {
                if (c.evaluate(exc, flow)) return c;
            }
        } catch (ExchangeExpressionException e) {
            handleExpressionProblemDetails(e, exc);
        }
        return null;
    }

    static void validateChoices(List<Choice> choices) {
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i);

            if (c instanceof Otherwise) {
                if (i != choices.size() - 1) {
                    throw new ConfigurationException("'otherwise' must be the last element in 'choose'.");
                }
            }
        }
    }

    private void setChoices() {
        for (Choice c : this.choices) {
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
            .detail("Error evaluating expression on exchange in if plugin.")
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
    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public List<Choice> getChoices() {
        return choices;
    }

}
