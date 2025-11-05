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
import com.predic8.membrane.core.config.xml.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;

public abstract class AbstractLanguageInterceptor extends AbstractInterceptor implements Polyglot, XMLSupport {

    /**
     * SpEL is default
     */
    protected Language language = SPEL;
    protected XmlConfig xmlConfig;

    public Language getLanguage() {
        return language;
    }

    /**
     * @description the language of the 'test' condition
     * @default SpEL
     * @example SpEL, groovy, jsonpath, xpath
     */
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
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