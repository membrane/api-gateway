package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;

@MCElement(name = "additionalPattern", topLevel = false, id = "accessLog-scope")
public class AdditionalPattern {

    private String create;
    private String withExchange;
    private String orDefaultValue = "-";
    private boolean override = true;

    public String getWithExchange() {
        return withExchange;
    }

    /**
     * @description The SPEL expression to access the property on an Exchange object
     */
    @Required
    @MCAttribute
    public void setWithExchange(String withExchange) {
        this.withExchange = withExchange;
    }

    public String getCreate() {
        return create;
    }

    /**
     * @description The key which can be used to access this value in log4j2.xml like %X{key}
     */
    @Required
    @MCAttribute
    public void setCreate(String create) {
        this.create = create;
    }

    public String getOrDefaultValue() {
        return orDefaultValue;
    }

    /**
     * @description The value if the exchange property is null. Defaults to "-"
     */
    @MCAttribute
    public void setOrDefaultValue(String orDefaultValue) {
        this.orDefaultValue = orDefaultValue;
    }

    public boolean isOverride() {
        return override;
    }

    /**
     * @description If it should override existing patterns, defaults to true
     */
    @MCAttribute
    public void setOverride(boolean override) {
        this.override = override;
    }
}
