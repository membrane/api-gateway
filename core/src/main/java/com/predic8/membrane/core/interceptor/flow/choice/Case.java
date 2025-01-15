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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;

@MCElement(name = "case", topLevel = false)
public class Case extends InterceptorContainer {

    private static final Logger log = LoggerFactory.getLogger(Case.class);

    private String test;
    private Language language = SPEL;
    private ExchangeExpression exchangeExpression;

    public void init(Router router) {
        exchangeExpression = ExchangeExpression.getInstance(router, language, test);
    }

    boolean evaluate(Exchange exc, Flow flow, Router router) throws ExchangeExpressionException {
        try {
            boolean result = exchangeExpression.evaluate(exc, flow, Boolean.class);
            log.debug("Expression {} evaluated to {}.", test, result);
            return result;
        } catch (NullPointerException npe) {
            // Expression evaluated to null and can't be converted to boolean
            // We assume that null is false
            log.debug("Expression {} returned null and is therefore interpreted as false", test);
            return false;
        }
    }

    public ExchangeExpression getExchangeExpression() {
        return exchangeExpression;
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
}