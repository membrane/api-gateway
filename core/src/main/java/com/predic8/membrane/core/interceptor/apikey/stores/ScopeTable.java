package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;

@MCElement(name = "scopeTable", mixed = true)
public class ScopeTable {

    private String name;


    public String getName() {
        return name;
    }

    @MCTextContent
    public void setName(String name) {
        this.name = name;
    }
}
