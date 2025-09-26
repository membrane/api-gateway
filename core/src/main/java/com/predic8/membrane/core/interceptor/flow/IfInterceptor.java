/* Copyright 2014 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.slf4j.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;

/**
 * @description <p>
 * if allows conditional execution of nested interceptors.
 * </p>
 * <pre><code><if test="method == 'POST'" language="SpEL">
 *         ...
 * </if></code></pre>
 * @topic 1. Proxies and Flow
 */
@MCElement(name = "if")
public class IfInterceptor extends AbstractFlowWithChildrenInterceptor {
    private static final Logger log = LoggerFactory.getLogger(IfInterceptor.class);

    private String test;
    private Language language = SPEL;

    private ExchangeExpression exchangeExpression;

    public IfInterceptor() {
        name = "if";
    }

    @Override
    public void init() {
        super.init();
        exchangeExpression = ExchangeExpression.newInstance(router, language, test);
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
        boolean result;
        try {
            result = exchangeExpression.evaluate(exc, flow, Boolean.class);
        } catch (ExchangeExpressionException e) {
            e.provideDetails(internal(router.isProduction(), getDisplayName()))
                    .detail("Error evaluating expression on exchange.")
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (NullPointerException npe) {
            // Expression evaluated to null and can't be converted to boolean
            // We assume that null is false
            log.debug("Expression {} returned null and is therefore interpreted as false", test);
            result = false;
        }
        if (log.isDebugEnabled())
            log.debug("Expression {} evaluated to {}.", test, result);

        if (!result)
            return CONTINUE;

        return switch (flow) {
            case REQUEST -> getFlowController().invokeRequestHandlers(exc, getFlow());
            case RESPONSE -> getFlowController().invokeResponseHandlers(exc, getFlow());
            default -> throw new RuntimeException("Should never happen");
        };
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * @description Language of the 'test' condition
     * @default SpEL
     * @example SpEL, groovy, jsonpath, xpath
     */
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getTest() {
        return test;
    }

    /**
     * @description Condition to be tested
     * @example <ul><li>request.isJSON()</li><li>params['limit'] >= 0</li><li>statusCode matches '[45]\d\d'</li></ul>
     */
    @Required
    @MCAttribute
    public void setTest(String test) {
        this.test = test;
    }

    @Override
    public String getShortDescription() {
        StringBuilder ret = new StringBuilder("if (" + test + ") {");
        for (Interceptor i : getFlow()) {
            ret.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;").append(i.getDisplayName());
        }
        ret.append("<br/>}");
        return ret.toString();
    }
}