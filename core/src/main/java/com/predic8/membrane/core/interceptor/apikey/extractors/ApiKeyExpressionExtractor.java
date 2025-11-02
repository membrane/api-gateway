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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;

import java.util.Optional;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.EXPRESSION;

/**
 * @deprecated Set the expression directly on the apiKey plugin.
 * @description Extracts an API key by evaluating an expression on the incoming request.
 * The result (a string) is treated as the API key. The expression is evaluated in the configured language
 * (default: <code>SPEL</code>) during the request flow.
 * <p>
 * Typical usage inside <code>&lt;apiKey&gt;</code>:
 * </p>
 * <pre><code><apiKey>
 *   <expressionExtractor
 *       language="SPEL"
 *       expression="request.headers['X-Api-Key']"/>
 * </apiKey></code></pre>
 * <p>
 * If the expression evaluates to <code>null</code> or an empty string, no key is extracted.
 * </p>
 * @topic 3. Security and Validation
 */
@MCElement(name="expressionExtractor", topLevel = false)
public class ApiKeyExpressionExtractor implements ApiKeyExtractor, Polyglot, XMLNamespaceSupport {

    private String expression = "";
    private Language language = SPEL;
    private ExchangeExpression exchangeExpression;
    private Namespaces namespaces;

    @Override
    public void init(Router router) {
        exchangeExpression = expression(new InterceptorAdapter(router,namespaces), language, expression);
    }

    @Override
    public Optional<LocationNameValue> extract(Exchange exc) {
        return Optional.of(new LocationNameValue(
                EXPRESSION,
                expression,
                exchangeExpression.evaluate(exc, REQUEST, String.class)
        ));
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

    /**
     * @description The expression evaluated against the message. It must resolve to a String
     * containing the API key. Empty or null results mean “no key found”.
     * <p>
     * Examples (SPEL):
     * </p>
     * <pre><code>expression="request.headers['X-Api-Key']"
     *  expression="request.query['api_key']"</code></pre>
     */
    @MCAttribute
    public void setExpression(String expression) {
        this.expression = expression;
    }

    /**
     * Declaration of XML namespaces for XPath expressions.
     * @param namespaces
     */
    @MCChildElement(allowForeign = true)
    public void setNamespaces(Namespaces namespaces) {
        this.namespaces = namespaces;
    }

    public Namespaces getNamespaces() {
        return namespaces;
    }
}
