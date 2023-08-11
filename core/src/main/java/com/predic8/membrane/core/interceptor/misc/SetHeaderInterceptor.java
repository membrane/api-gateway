/* Copyright 2022 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.spel.*;
import org.slf4j.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;
import org.springframework.expression.spel.support.*;

import java.util.Arrays;
import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name = "setHeader")
public class SetHeaderInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SetHeaderInterceptor.class.getName());

    private final Pattern expressionPattern = Pattern.compile("\\$\\{(.*?)}");

    /**
     * SpelExpressionParser is reusable and thread-safe
     */
    private static final ExpressionParser parser = new SpelExpressionParser();

    private String name;
    private String value;
    private boolean ifAbsent;

    public boolean isIfAbsent() {
        return ifAbsent;
    }

    @MCAttribute
    public void setIfAbsent(boolean ifAbsent) {
        this.ifAbsent = ifAbsent;
    }

    public String getName() {
        return name;
    }

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    @MCAttribute
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleMessage(exc, exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleMessage(exc, exc.getResponse());
    }

    private Outcome handleMessage(Exchange exchange, Message msg) {
        var msgContainsHeader = Arrays.stream(msg.getHeader().getAllHeaderFields()).anyMatch(headerField -> headerField.getHeaderName().equals(name));

        if (!ifAbsent || !msgContainsHeader) {
            msg.getHeader().setValue(name, evaluateExpression(new ExchangeEvaluationContext(exchange, msg).getStandardEvaluationContext()));
        }

        return CONTINUE;
    }

    protected String evaluateExpression(StandardEvaluationContext evalCtx) {
        return expressionPattern.matcher(value).replaceAll(m -> evaluateSingeExpression(evalCtx, m.group(1)));
    }

    protected String evaluateSingeExpression(StandardEvaluationContext evalCtx, String expression) {
        String result = parser.parseExpression(expression).getValue(evalCtx, String.class);
        if (result != null)
            return result;

        log.info("The expression {} evaluates to null or there is an error in the expression.", expression);
        return "null";
    }

    @Override
    public String getDisplayName() {
        return "setHeader";
    }

    @Override
    public String getShortDescription() {
        return String.format("Sets the value of the HTTP header '%s' to %s.",name,value);
    }
}