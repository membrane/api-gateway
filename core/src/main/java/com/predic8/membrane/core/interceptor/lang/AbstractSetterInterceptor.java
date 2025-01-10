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

package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;

public abstract class AbstractSetterInterceptor extends AbstractLanguageInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractSetterInterceptor.class);

    private boolean failOnError = true;

    protected String name; // TODO rename shadows name from AbstractInterceptor
    protected boolean ifAbsent;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        exchangeExpression = new TemplateExchangeExpression(router,language,expression);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exchange, Flow flow) {
        if (!shouldSetValue(exchange, flow))
            return CONTINUE;

        try {
            setValue(exchange, flow, exchangeExpression.evaluate(exchange, flow, Object.class));
        } catch (Exception e) {
            if (failOnError) {
                ProblemDetails.internal(getRouter().isProduction())
                        .title("Error evaluating expression!")
                        .component(getName())
                        .extension("field", name)
                        .extension("value", expression)
                        .buildAndSetResponse(exchange);
                return ABORT;
            }
        }
        return CONTINUE;
    }

    protected abstract boolean shouldSetValue(Exchange exchange, Flow flow);

    protected abstract void setValue(Exchange exchange, Flow flow, Object evaluatedValue);

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
        this.expression = value;
    }

    public String getValue() {
        return expression;
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
