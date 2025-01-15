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
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.slf4j.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;

/**
 * @description <p>
 * The "if" interceptor supports conditional execution of nested plugins.
 * </p>
 *
 * See:
 * - com.predic8.membrane.core.interceptor.flow.IfInterceptorSpELTest
 * - com.predic8.membrane.core.interceptor.flow.IfInterceptorGroovyTest
 * - com.predic8.membrane.core.interceptor.flow.IfInterceptorJsonpathTest
 * - com.predic8.membrane.core.interceptor.flow.IfInterceptorXPathTest
 */
@MCElement(name = "if")
public class IfInterceptor extends AbstractFlowInterceptor {
    private static final Logger log = LoggerFactory.getLogger(IfInterceptor.class);

    private String test;
    private Language language = SPEL;

    private ExchangeExpression exchangeExpression;

    public IfInterceptor() {
        name = "If";
    }

    @Override
    public void init() {
        super.init();
        exchangeExpression = ExchangeExpression.getInstance(router, language, test);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) throws Exception {
        boolean result;
        try {
             result = exchangeExpression.evaluate(exc, flow, Boolean.class);
        } catch (ExchangeExpressionException e) {
            e.provideDetails(ProblemDetails.internal(router.isProduction()))
                    .detail("Error evaluating expression on exchange in if plugin.")
                    .component("if")
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (NullPointerException npe) {
            // Expression evaluated to null and can't be converted to boolean
            // We assume that null is false
            log.debug("Expression {} returned null and is therefore interpreted as false",test);
            result = false;
        }
        if (log.isDebugEnabled())
            log.debug("Expression {} evaluated to {}.", test, result);

        if (!result)
            return CONTINUE;

        return switch (flow) {
            case REQUEST -> getFlowController().invokeRequestHandlers(exc, getInterceptors());
            case RESPONSE -> getFlowController().invokeResponseHandlers(exc, getInterceptors());
            default -> throw new RuntimeException("Should never happen");
        };
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * @description the language of the 'test' condition
     * @default groovy
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
     * @description the condition to be tested
     * @example exc.request.header.userAgentSupportsSNI
     */
    @Required
    @MCAttribute
    public void setTest(String test) {
        this.test = test;
    }

    @Override
    public String getShortDescription() {
        StringBuilder ret = new StringBuilder("if (" + test + ") {");
        for (Interceptor i : getInterceptors()) {
            ret.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;").append(i.getDisplayName());
        }
        ret.append("<br/>}");
        return ret.toString();
    }
}