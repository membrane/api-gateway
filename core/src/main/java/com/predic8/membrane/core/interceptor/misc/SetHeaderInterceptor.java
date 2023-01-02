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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;
import org.springframework.expression.spel.support.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "setHeader")
public class SetHeaderInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SetHeaderInterceptor.class.getName());

    ObjectMapper om = new ObjectMapper();

    private final Pattern expressionPattern = Pattern.compile("\\$\\{(.*?)}");

    private final ExpressionParser parser = new SpelExpressionParser();

    private String name;
    private String value;

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
        msg.getHeader().setValue(name, evaluateExpression(getEvalContext(exchange, msg)));
        return CONTINUE;
    }

    private StandardEvaluationContext getEvalContext(Exchange exchange, Message msg) {
        EvalContext ctx = new EvalContext();
        ctx.setExchange(exchange);
        ctx.setProperties(exchange.getProperties());
        ctx.setMessage(msg);
        ctx.setHeaders(msg.getHeader());
        return new StandardEvaluationContext(ctx);
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

    class EvalContext {
        Exchange exchange;
        Message message;
        Header headers;
        Map<String, Object> properties;

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }

        public Header getHeaders() {
            return headers;
        }

        public void setHeaders(Header headers) {
            this.headers = headers;
        }

        public Exchange getExchange() {
            return exchange;
        }

        public void setExchange(Exchange exchange) {
            this.exchange = exchange;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public Map getJson() throws IOException {
            return om.readValue(message.getBodyAsStreamDecoded(), Map.class);
        }

        public void setJson(Map<String, Object> json) {
//            this.json = json;
        }
    }
}