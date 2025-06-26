package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.ArrayList;
import java.util.List;

@MCElement(name = "fields")
public class Fields {

    private List<Field> fields = new ArrayList<>();

    @MCChildElement
    public Fields setFields(List<Field> fields) {
        this.fields = fields;
        return this;
    }

    public List<Field> getFields() {
        return fields;
    }
}
