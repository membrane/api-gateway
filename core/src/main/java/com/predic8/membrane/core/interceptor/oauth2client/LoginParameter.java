package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "loginParameter")
public class LoginParameter {

    private String name;
    private String value;

    public String getName() {
        return name;
    }

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    @MCAttribute
    public void setValue(String value) {
        this.value = value;
    }
}
