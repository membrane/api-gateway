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

package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.groovy.*;
import com.predic8.membrane.core.lang.jsonpath.*;
import com.predic8.membrane.core.lang.spel.*;
import com.predic8.membrane.core.lang.xpath.*;

/**
 * Language expression that takes an exchange as input
 */
public interface ExchangeExpression {

    enum Language {GROOVY, SPEL, XPATH, JSONPATH}

    /**
     * Override this with the logic to evaluate the expression on the exchange
     * Caller is responsible for exception handling. But caller can delegate it back to the
     * fill() method.
     * @param exchange
     * @param flow
     * @param type
     * @return
     * @param <T>
     * @throws Exception
     */
    <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type);

    static ExchangeExpression getInstance(Router router, Language language, String source) {
        return switch (language) {
            case GROOVY -> new GroovyExchangeExpression(router, source);
            case SPEL -> new SpELExchangeExpression(source,null);
            case XPATH -> new XPathExchangeExpression(source);
            case JSONPATH -> new JsonpathExchangeExpression(source);
        };
    }

}
