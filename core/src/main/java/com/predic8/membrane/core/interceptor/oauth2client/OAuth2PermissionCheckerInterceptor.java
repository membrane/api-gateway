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
package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.util.functionalInterfaces.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.predic8.membrane.annot.Required;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;

@MCElement(name = "oauth2PermissionChecker")
public class OAuth2PermissionCheckerInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OAuth2PermissionCheckerInterceptor.class);

    String expression;
    ValueSource valueSource;
    Function<Object, Boolean> valueChecker;

    public String getExpression() {
        return expression;
    }

    @MCAttribute
    public void setExpression(String expression) {
        this.expression = expression;
    }

    public ValueSource getValueSource() {
        return valueSource;
    }

    @MCChildElement(order = 50)
    public void setValueSource(ValueSource valueSource) {
        this.valueSource = valueSource;
    }

    @Override
    public void init() throws Exception {
        valueChecker = createChecker(expression);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        Object value = valueSource.evaluate(exc);
        if (!valueChecker.call(value)) {
            log.warn("OAuth2 permission check " + expression + " failed on value " + value);
            exc.setResponse(Response.forbidden().build());
            return Outcome.RETURN;
        }
        return super.handleRequest(exc);
    }

    public static abstract class ValueSource {
        public abstract Object evaluate(Exchange exc);
    }

    @MCElement(topLevel = false, name = "userInfo")
    public static class UserInfoValueSource extends ValueSource {
        String field;

        public String getField() {
            return field;
        }

        @Required
        @MCAttribute
        public void setField(String field) {
            this.field = field;
        }

        @Override
        public Object evaluate(Exchange exc) {
            Object oauth2prop = exc.getProperty("oauth2");
            if (oauth2prop == null)
                return null;
            return ((OAuth2AnswerParameters)oauth2prop).getUserinfo().get("groups");
        }
    }

    private Function<Object, Boolean> createChecker(String expr) {
        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(expr);

        return param -> {
            if (!(param instanceof List))
                return false;

            ExpressionContext ec = new ExpressionContext((List) param);
            StandardEvaluationContext simpleContext = new StandardEvaluationContext(ec);
            return exp.getValue(simpleContext, Boolean.class);
        };
    }

    public class ExpressionContext {
        private final List list;

        public ExpressionContext(List list) {
            this.list = list;
        }

        public boolean contains(Object value) {
            return list.contains(value);
        }
    }

}
