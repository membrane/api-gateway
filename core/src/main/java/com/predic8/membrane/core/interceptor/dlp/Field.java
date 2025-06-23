package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "field")
public class Field {

    private String name;
    private String action;


    public String getName() {
        return name;
    }

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getAction() {
        return action;
    }

    @MCAttribute
    public void setAction(String action) {
        this.action = action;
    }
}
