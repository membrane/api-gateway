package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "field")
public class Field {

    private String jsonpath;

    public String getJsonpath() {
        return jsonpath;
    }

    @MCAttribute
    public void setJsonpath(String jsonpath) {
        this.jsonpath = jsonpath;
    }
}
