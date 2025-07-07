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
package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.lang.Polyglot;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;

import java.io.IOException;
import java.util.Optional;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.EXPRESSION;

@MCElement(name="expressionExtractor", topLevel = false)
public class ApiKeyExpressionExtractor implements ApiKeyExtractor, Polyglot {

    private String expression = "";
    private Language language = SPEL;
    private ExchangeExpression exchangeExpression;

    @Override
    public void init(Router router) {
        exchangeExpression = ExchangeExpression.newInstance(router, language, expression);
    }

    @Override
    public Optional<LocationNameValue> extract(Exchange exc) {
        try {
            return Optional.of(new LocationNameValue(
                    EXPRESSION,
                    expression,
                    exchangeExpression.evaluate(exc, REQUEST, String.class)
            ));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDescription() {
        return "Expression: " + expression + ". ";
    }

    @Override
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getExpression() {
        return expression;
    }

    @MCAttribute
    public void setExpression(String expression) {
        this.expression = expression;
    }
}
