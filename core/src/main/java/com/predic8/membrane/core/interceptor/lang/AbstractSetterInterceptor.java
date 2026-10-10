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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import com.predic8.membrane.core.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

public abstract class AbstractSetterInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractSetterInterceptor.class);

    private boolean failOnError = true;

    protected String fieldName;
    protected boolean ifAbsent;

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
            setValue(exchange, flow, exchangeExpression.evaluate(exchange, flow, getExpressionReturnType()));
        } catch (Exception e) {
            var msg = "Error evaluating expression %s for field %s".formatted(expression, fieldName);
            if (failOnError) {
                if (e instanceof ExchangeExpressionException eee) {
                    var pd = prepareProblemDetails(eee.emptyProblemDetails(isProduction(), getDisplayName(), flow), msg);
                    eee.provideDetails(pd);
                    pd.buildAndSetResponse(exchange);
                    return ABORT;
                }
                prepareProblemDetails(internal(isProduction(), getDisplayName()), msg)
                        .exception(ExceptionUtil.getRootCause(e))
                        .stacktrace(false)
                        .buildAndSetResponse(exchange);
                return ABORT;
            }
            log.info("'FailOnError' is false therefore ignoring: {}", msg);
        }
        return CONTINUE;
    }

    private boolean isProduction() {
        return getRouter().getConfiguration().isProduction();
    }

    private ProblemDetails prepareProblemDetails(ProblemDetails pd, String msg) {
        return pd
                .title(msg)
                .internal("field", fieldName)
                .internal("expression", expression);
    }

    protected abstract Class<?> getExpressionReturnType();

    protected abstract boolean shouldSetValue(Exchange exchange, Flow flow);

    protected abstract void setValue(Exchange exchange, Flow flow, Object evaluatedValue);

    /**
     * @description When true, only sets the field if it is not already present in the message.
     * @default false
     * @example true
     */
    @MCAttribute
    public void setIfAbsent(boolean ifAbsent) {
        this.ifAbsent = ifAbsent;
    }

    public boolean getIfAbsent() {
        return ifAbsent;
    }

    /**
     * @description Name of the header field or exchange property key to set.
     * @example X-Powered-By
     */
    @MCAttribute(attributeName = "name")
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    /**
     * @description Value to assign, evaluated as a SpEL template expression by default.
     * Use the <code>language</code> attribute to switch to Groovy, JsonPath, or XPath.
     * <p>XPath and JSONPath query the body. An empty body, or one of another media type, is evaluated as an
     * empty document, so the value is empty. A body that should be XML or JSON but does not parse is an error:
     * 400 in the request, 502 in the response, unless <code>failOnError</code> is false.</p>
     * @example ${method}
     */
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
     * @description When true, aborts processing if expression evaluation fails.
     * When false, logs the error and continues unchanged.
     * @default true
     * @example false
     */
    @MCAttribute
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

}
