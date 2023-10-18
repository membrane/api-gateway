/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */


package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;

@MCElement(name = "additionalVariable", topLevel = false, id = "accessLog-scope")
public class AdditionalVariable {

    private String name;
    private String expression;
    private String defaultValue = "-";

    public String getExpression() {
        return expression;
    }

    /**
     * @description The SPEL expression to access the property on an ExchangeEvaluationContext
     * Using camelCased headers like camelHeader will resolve to camel-header if the camelHeader is not present.
     */
    @Required
    @MCAttribute
    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getName() {
        return name;
    }

    /**
     * @description The key which can be used to access this value in log4j2.xml like %X{key}
     */
    @Required
    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * @description The value if the exchange property is null. Defaults to "-"
     */
    @MCAttribute
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
