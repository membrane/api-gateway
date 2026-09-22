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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;

/**
 * @description Sets an exchange property to a constant string or a computed value. Unlike a header or
 * the body, a property is not sent to the client; it is only readable by later interceptors in the same
 * exchange, e.g. as <code>${property.name}</code> in a template or another expression. The value is
 * evaluated as a SpEL template, Groovy, JsonPath, or XPath expression, defaulting to SpEL.
 * See tutorials/orchestration/30-Orchestration.yaml.
 * @topic 2. Enterprise Integration Patterns
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - setProperty:
 *          name: product
 *          value: ${$.product_id}
 *          language: jsonpath
 *     - call:
 *         url: https://api.predic8.de/shop/v2/${pathParam.product}
 *
 * </code></pre>
 */
@MCElement(name = "setProperty")
public class SetPropertyInterceptor extends AbstractSetterInterceptor {

    @SuppressWarnings("rawtypes")
    @Override
    protected Class<?> getExpressionReturnType() {
        return Object.class;
    }

    @Override
    protected boolean shouldSetValue(Exchange exchange, Flow ignored) {
        if (ifAbsent) {
            return exchange.getProperty(fieldName) == null;
        }
        return true;
    }

    @Override
    protected void setValue(Exchange exchange, Flow flow, Object evaluatedValue) {
        exchange.setProperty(fieldName, evaluatedValue);
    }

    @Override
    public String getDisplayName() {
        return "setProperty";
    }

    @Override
    public String getShortDescription() {
        return "Sets the value of the exchange property '%s' to %s.".formatted(fieldName, expression);
    }
}