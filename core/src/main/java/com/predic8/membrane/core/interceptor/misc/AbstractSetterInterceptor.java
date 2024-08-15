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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

public abstract class AbstractSetterInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractSetterInterceptor.class);
    private final SpelParserConfiguration spelConfig = new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, this.getClass().getClassLoader());
    private final ExpressionParser parser = new SpelExpressionParser(spelConfig);
    private final Pattern expressionPattern = Pattern.compile("\\$\\{(.*?)}");

    protected String name;
    protected String value;
    protected boolean ifAbsent;

    private Outcome handleMessage(Exchange exchange, Message msg, Flow flow) {
        if (shouldSetValue(exchange, flow)) {
            setValue(exchange, flow, evaluateExpression(new ExchangeEvaluationContext(exchange, msg)));
        }

        return CONTINUE;
    }

    protected abstract boolean shouldSetValue(Exchange exchange, Flow flow);
    protected abstract void setValue(Exchange exchange, Flow flow, String evaluatedValue);

    protected String evaluateExpression(StandardEvaluationContext evalCtx) {
        return expressionPattern.matcher(value).replaceAll(m -> evaluateSingleExpression(evalCtx, m.group(1)));
    }

    protected String evaluateSingleExpression(StandardEvaluationContext evalCtx, String expression) {
        String result = parser.parseExpression(expression).getValue(evalCtx, String.class);
        if (result != null)
            return result;

        log.info("The expression {} evaluates to null or there is an error in the expression.", expression);
        return "null";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleMessage(exc, exc.getRequest(), REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleMessage(exc, exc.getResponse(), RESPONSE);
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
}
