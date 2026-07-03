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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.TemplateExchangeExpression;

import static com.predic8.membrane.core.util.text.SerializationFunction.HEADERVALUE_SERIALIZATION;

/**
 * @description Sets an HTTP header field on the current message to a constant string or a computed value.
 * SpEL template expressions are supported by default; Groovy, JsonPath, and XPath are also available.
 * See tutorials/getting-started/60-SetHeader.yaml.
 * @topic 2. Enterprise Integration Patterns
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - response:
 *         - setHeader:
 *             name: X-Powered-By
 *             value: Membrane
 *         - setHeader:
 *             name: X-Method
 *             value: ${method}
 *     - return:
 *         status: 200
 * </code></pre>
 */
@MCElement(name = "setHeader")
public class SetHeaderInterceptor extends AbstractSetterInterceptor {

    @Override
    protected Class<?> getExpressionReturnType() {
        return String.class;
    }

    @Override
    protected boolean shouldSetValue(Exchange exc, Flow flow) {
        if (ifAbsent) {
            return !exc.getMessage(flow).getHeader().contains(fieldName);
        }
        return true;
    }

    @Override
    protected ExchangeExpression getExchangeExpression() {
        return TemplateExchangeExpression.newInstance(this, language, expression, router, HEADERVALUE_SERIALIZATION);
    }

    @Override
    protected void setValue(Exchange exc, Flow flow, Object value) {
        exc.getMessage(flow).getHeader().setValue(fieldName, value.toString());
    }

    @Override
    public String getDisplayName() {
        return "setHeader";
    }

    @Override
    public String getShortDescription() {
        return "Sets the value of the HTTP header '%s' to the expression: %s.".formatted(fieldName, expression);
    }
}
