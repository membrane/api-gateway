/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.slf4j.*;

import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;

public abstract class AbstractSetterInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractSetterInterceptor.class);

    private Language language = SPEL;

    private boolean failOnError = true;

    private final Pattern expressionPattern = Pattern.compile("\\$\\{(.*?)}");

    protected String name;
    protected String value;
    protected boolean ifAbsent;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, exc.getRequest(), REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, exc.getResponse(), RESPONSE);
    }

    private Outcome handleInternal(Exchange exchange, Message msg, Flow flow) {
        if (shouldSetValue(exchange, flow)) {
            try {
                setValue(exchange, flow, evaluateExpression(exchange, msg));
            } catch (Exception e) {
                if (failOnError) {
                    ProblemDetails.internal(getRouter().isProduction())
                            .title("Error evaluating expression!")
                            .extension("field", name)
                            .extension("value", value)
                            .buildAndSetResponse(exchange);
                    return Outcome.ABORT;
                }
            }
        }
        return CONTINUE;
    }

    protected abstract boolean shouldSetValue(Exchange exchange, Flow flow);

    protected abstract void setValue(Exchange exchange, Flow flow, String evaluatedValue);

    protected String evaluateExpression(Exchange exc, Message msg) {
        return expressionPattern.matcher(value).replaceAll(m -> evaluateSingleExpression(exc, msg, m.group(1)));
    }

    protected String evaluateSingleExpression(Exchange exc, Message msg, String expression) {
        String result = ExchangeExpression.getInstance(router, language, expression).evaluate(exc, REQUEST, String.class);
        if (result != null)
            return result;
        log.debug("The expression {} evaluates to null or there is an error in the expression.", expression);
        return "";
    }

    @MCAttribute
    public void setIfAbsent(boolean ifAbsent) {
        this.ifAbsent = ifAbsent;
    }

    public boolean getIfAbsent() {
        return ifAbsent;
    }

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @MCAttribute
    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
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

    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * Sets whether errors during value evaluation should be ignored or throw an exception.
     *
     * @param failOnError If true, an exception is raised on error. If false, errors are ignored.
     * @default true
     */
    @MCAttribute
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

}
