package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.Required;

public abstract class Rule {

    protected String method; // TODO to pattern

    @Required
    @MCAttribute
    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }
}
