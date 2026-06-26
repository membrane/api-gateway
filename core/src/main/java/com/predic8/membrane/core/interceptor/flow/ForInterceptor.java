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
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;

/**
 * @description Iterates over a collection and runs its nested flow once per element. The
 * <code>in</code> expression is evaluated against the exchange and must yield a <code>List</code>;
 * for each item the nested plugins run with the current item exposed as the exchange property
 * <code>it</code>. Iteration happens only in the request flow and only when the expression yields a
 * list; any other value passes through unchanged. If the expression fails to evaluate, the exchange
 * is aborted with a Problem Details response. See the examples and tutorials under
 * examples/orchestration and tutorials/orchestration.
 * <pre>
 * for:
 *   in: expression                # must evaluate to a List
 *   [ language: SpEL | groovy | jsonpath | xpath ]   # default: SpEL
 *   flow:                         # runs once per item; current item in property "it"
 *     - ...
 * </pre>
 * @topic 1. Proxies and Flow
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - for:
 *         in: message.json.items
 *         flow:
 *           - log: {}
 * </code></pre>
 */
@MCElement(name = "for")
public class ForInterceptor extends AbstractFlowWithChildrenInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ForInterceptor.class);

    private String in;
    private Language language = SPEL;

    private ExchangeExpression exchangeExpression;

    @Override
    public void init() {
        super.init();
        try {
            exchangeExpression = expression(this, language, in);
        } catch (ConfigurationException ce) {
            throw new ConfigurationException(ce.getMessage() + """
                    
                    <for in="%s">""".formatted(in), ce.getCause());
        }
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
        Object o;
        try {
            o = exchangeExpression.evaluate(exc, flow, Object.class);
        } catch (ExchangeExpressionException e) {
           ProblemDetails pd =  ProblemDetails.internal(router.getConfiguration().isProduction(), getDisplayName());
            e.provideDetails(pd)
                    .detail("Error evaluating expression on exchange.")
                    .component(getDisplayName())
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        if (o instanceof List<?> l) {
            log.debug("List detected {}",l);
            for (Object o2 : l) {
                log.debug("type: {}, it: {}",o2.getClass(),o2);
                exc.setProperty("it", o2);
                Outcome outcome = invokeFlowHandlers(exc, flow, interceptors);
                if (outcome != CONTINUE)
                    return outcome;
            }
        }

        return CONTINUE;
    }

    private Outcome invokeFlowHandlers(Exchange exc, Flow flow, List<Interceptor> interceptors) {
        return switch (flow) {
            case REQUEST -> getFlowController().invokeRequestHandlers(exc, interceptors);
            case RESPONSE -> getFlowController().invokeResponseHandlers(exc, interceptors);
            default -> throw new RuntimeException("Should never happen");
        };
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * @description Expression language used to evaluate <code>in</code>.
     * @default SpEL
     * @example groovy
     */
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getIn() {
        return in;
    }

    /**
     * @description Expression that selects the collection to iterate over; it must evaluate to a
     * <code>List</code>.
     * @example message.json.customers
     */
    @Required
    @MCAttribute
    public void setIn(String in) {
        this.in = in;
    }

    @Override
    public String getDisplayName() {
        return "for";
    }

    @Override
    public String getShortDescription() {
        StringBuilder ret = new StringBuilder("for (" + in + ") {");
        for (Interceptor i : getFlow()) {
            ret.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;").append(i.getDisplayName());
        }
        ret.append("<br/>}");
        return ret.toString();
    }
}
