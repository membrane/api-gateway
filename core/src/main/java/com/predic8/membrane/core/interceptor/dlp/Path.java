package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;

@MCElement(name = "xpath", mixed = true)
public class Path {

    private String jsonpath;

    public String getJsonpath() {
        return jsonpath;
    }

    @MCTextContent
    public void setJsonpath(String jsonpath) {
        this.jsonpath = jsonpath;
    }
}
