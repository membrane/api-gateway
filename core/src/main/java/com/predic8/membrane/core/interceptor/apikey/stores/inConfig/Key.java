package com.predic8.membrane.core.interceptor.apikey.stores.inConfig;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.ArrayList;
import java.util.List;

@MCElement(name = "keyDef", topLevel = false)
public class Key {

    private final List<Scope> scopes = new ArrayList<>();

    private String value;

    @MCChildElement(allowForeign = true)
    public void setScopes(List<Scope> scopes) {
        this.scopes.addAll(scopes);
    }

    public List<Scope> getScopes() {
        return scopes;
    }

    @MCAttribute
    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
