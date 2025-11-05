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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.xml.*;
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
     *
     * @return String from which the ExchangeExpression was created
     */
    String getExpression();

    /**
     * Override this with the logic to evaluate the expression on the exchange
     * Caller is responsible for exception handling. But caller can delegate it back to the
     * fill() method.
     * @param exchange
     * @param flow
     * @param type
     * @return
     * @param <T>
     * @throws ExchangeExpressionException
     */
    <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) throws ExchangeExpressionException;

    /**
     * Clients of this class should pass an interceptor if possible. Otherwise use the InterceptorAdapter to wrap it.
     * There is no convenience method on purpose to make the clients pass the interceptor. From the interceptor you can always get the router.
     * @param interceptor
     * @param language
     * @param expression
     * @return
     */
    static ExchangeExpression expression(Interceptor interceptor, Language language, String expression) {
        return switch (language) {
            case GROOVY -> new GroovyExchangeExpression(interceptor, expression);
            case SPEL -> new SpELExchangeExpression(expression,null);
            case XPATH -> new XPathExchangeExpression(interceptor,expression);
            case JSONPATH -> new JsonpathExchangeExpression(expression);
        };
    }

    /**
     * Allows to pass an Interceptor as an argument where there is no interceptor e.g. Target
     */
    class InterceptorAdapter extends AbstractInterceptor implements XMLSupport {

        private XmlConfig xmlConfig;

        public InterceptorAdapter(Router router) {
            this.router = router;
        }

        public InterceptorAdapter(Router router, XmlConfig xmlConfig) {
            this.router = router;
            this.xmlConfig = xmlConfig;
        }

        /**
         * XML Configuration e.g. declaration of XML namespaces for XPath expressions, ...
         * @param xmlConfig
         */
        @Override
        @MCChildElement(allowForeign = true,order = 10)
        public void setXmlConfig(XmlConfig xmlConfig) {
            this.xmlConfig = xmlConfig;
        }

        @Override
        public XmlConfig getXmlConfig() {
            return xmlConfig;
        }
    }
}
