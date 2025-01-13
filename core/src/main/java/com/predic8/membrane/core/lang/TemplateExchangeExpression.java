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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import org.slf4j.*;

import java.util.*;
import java.util.regex.*;

public class TemplateExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(TemplateExchangeExpression.class);

    /**
     * For parsing strings with expressions inside ${} e.g. "foo ${property.bar} baz"
     */
    private static final Pattern scriptPattern = Pattern.compile("([^$]+)?(\\$\\{(.*?)})?|");

    private final List<Token> tokens;

    public TemplateExchangeExpression(Router router, Language language, String expression) {
        super(expression);
        tokens = parseTokens(router,language, expression);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Flow flow, Class<T> type) {
        StringBuilder line = new StringBuilder();
        for(Token token : tokens) {
            try {
                line.append(token.eval(exchange, flow));
            } catch (Exception e) {
                throw new ExchangeExpressionException(token.toString(),e);
            }
        }
        return (T) line.toString();
    }

    protected static List<Token> parseTokens(Router router, Language language, String expression) {
        log.debug("Parsing: {}",expression);

        List<Token> tokens = new ArrayList<>();
        Matcher m = scriptPattern.matcher(expression);
        while (m.find()) {
            String text = m.group(1);
            if (text != null) {
                tokens.add(new Text(text));
            }
            String expr = m.group(3);
            if (expr != null) {
                tokens.add(new Expression(ExchangeExpression.getInstance(router, language, expr)));
            }
        }
        log.debug("Tokens: {}", tokens);
        return tokens;
    }

    interface Token {
        String eval(Exchange exchange, Flow flow) throws Exception;
        String getExpression();
    }

    static class Text implements Token {

        private final String value;

        public Text(String value) {
            this.value = value;
        }

        @Override
        public String eval(Exchange exchange, Flow flow) {
            return value;
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
        public String eval(Exchange exchange, Flow flow) throws Exception {
            return exchangeExpression.evaluate(exchange, flow, String.class);
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
