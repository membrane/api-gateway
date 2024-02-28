package com.predic8.membrane.core.interceptor.apikey.stores.inConfig;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;

@MCElement(name = "scope", topLevel = false, mixed = true)
public class Scope {

    private String value;

    public String getValue() {
        return value;
    }

    @MCTextContent
    public void setValue(String value) {
        this.value = value;
    }

}
