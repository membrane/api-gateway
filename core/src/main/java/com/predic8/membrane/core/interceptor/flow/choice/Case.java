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
package com.predic8.membrane.core.interceptor.flow.choice;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.xml.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.slf4j.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;

@MCElement(name = "case", topLevel = false)
public class Case extends InterceptorContainer implements XMLSupport {

    private static final Logger log = LoggerFactory.getLogger(Case.class);

    private String test;
    private Language language = SPEL;
    private ExchangeExpression exchangeExpression;
    private XmlConfig xmlConfig;

    public void init(Router router) {
        exchangeExpression = expression( new InterceptorAdapter(router,xmlConfig), language, test);
    }

    boolean evaluate(Exchange exc, Flow flow) {
        boolean result = exchangeExpression.evaluate(exc, flow, Boolean.class);
        log.debug("Expression {} evaluated to {}.", test, result);
        return result;
    }

    public ExchangeExpression getExchangeExpression() {
        return exchangeExpression;
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * @description the language of the 'test' condition
     * @default groovy
     * @example SpEL, groovy, jsonpath, xpath
     */
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getTest() {
        return test;
    }

    /**
     * @description the condition to be tested
     * @example exc.request.header.userAgentSupportsSNI
     */
    @Required
    @MCAttribute
    public void setTest(String test) {
        this.test = test;
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