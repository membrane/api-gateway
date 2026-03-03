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
package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.text.*;
import org.slf4j.*;
import org.w3c.dom.*;

import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.lang.TemplateExpressionParser.*;
import static com.predic8.membrane.core.util.text.SerializationFunction.*;

public class TemplateExchangeExpression extends AbstractExchangeExpression {

    /**
     * Plugable encoder to apply various encoding strategies like URL, or path segment encoding.
     */
    private final SerializationFunction encoder;

    private final List<Token> tokens;

    public static ExchangeExpression newInstance(Interceptor interceptor, Language language, String expression, Router router) {
        return newInstance(interceptor, language, expression, router, IDENTITY_SERIALIZATION);
    }

    public static ExchangeExpression newInstance(Interceptor interceptor, Language language, String expression, Router router, SerializationFunction encoder) {
        // SpEL can take expressions like "a: ${..} b: ${..}" as input. We do not use that feature and tokenize the expression ourselves to enable encoding
        return new TemplateExchangeExpression(interceptor, language, expression, router, encoder);
    }

    protected TemplateExchangeExpression(Interceptor interceptor, Language language, String expression, Router router, SerializationFunction encoder) {
        super(expression, router);
        this.encoder = encoder;
        tokens = parseTokens(interceptor, language, expression);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Flow flow, Class<T> type) {
        if (tokens.isEmpty()) {
            return null;
        }
        if (tokens.size() == 1) {
            if (type.getName().equals(String.class.getName())) {
                return type.cast(evaluateMultiple(exchange, flow));
            }
            return type.cast(evaluateSingle(exchange, flow));
        }
        return type.cast(evaluateMultiple(exchange, flow));
    }

    private Object evaluateSingle(Exchange exchange, Flow flow) {
        try {
            return tokens.getFirst().eval(exchange, flow, Object.class);
        } catch (Exception e) {
            throw new ExchangeExpressionException(tokens.getFirst().toString(), e);
        }
    }

    private String evaluateMultiple(Exchange exchange, Flow flow) {
        var line = new StringBuilder();
        for (var token : tokens) {
            try {
                var value = token.eval(exchange, flow, Object.class);
                if (token instanceof Text) {
                    line.append(value);
                    continue;
                }
                if (value == null) {
                    line.append("null");
                    continue;
                }
                if (value instanceof Node n) {
                    value = n.getTextContent();
                }
                line.append(encoder.apply(value));
            } catch (Exception e) {
                throw new ExchangeExpressionException(token.toString(), e);
            }
        }
        return line.toString();
    }

    interface Token {
        <T> T eval(Exchange exchange, Flow flow, Class<T> type);

        String getExpression();
    }

    static class Text implements Token {

        private final String value;

        public Text(String value) {
            this.value = value;
        }

        @Override
        public <T> T eval(Exchange exchange, Flow flow, Class<T> type) {
            return type.cast(value);
        }

        @Override
        public String getExpression() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Text t) {
                return value.equals(t.value);
            }
            return false;
        }

        @Override
        public String toString() {
            return "Text(%s)".formatted(value);
        }
    }

    static class Expression implements Token {

        private final ExchangeExpression exchangeExpression;

        public Expression(ExchangeExpression exchangeExpression) {
            this.exchangeExpression = exchangeExpression;
        }

        @Override
        public <T> T eval(Exchange exchange, Flow flow, Class<T> type) {
            return exchangeExpression.evaluate(exchange, flow, type);
        }

        @Override
        public String getExpression() {
            return exchangeExpression.toString();
        }

        @Override
        public String toString() {
            return "Expr(%s)".formatted(exchangeExpression);
        }
    }
}
