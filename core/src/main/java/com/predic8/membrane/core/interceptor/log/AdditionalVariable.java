package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;

@MCElement(name = "additionalPattern", topLevel = false, id = "accessLog-scope")
public class AdditionalVariable {

    private String name;
    private String expression;
    private String defaultValue = "-";

    public String getExpression() {
        return expression;
    }

    /**
     * @description The SPEL expression to access the property on an ExchangeEvaluationContext
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