package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCAttribute;

public abstract class Action implements DLPAction {

    private String field;

    public String getField() {
        return field;
    }

    @MCAttribute
    public void setField(String field) {
        this.field = field;
    }

    @Override
    public abstract String apply(String json, DLPContext context);
}
