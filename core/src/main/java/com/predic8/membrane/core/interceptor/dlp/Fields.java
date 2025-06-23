package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "fields")
public class Fields {

    private Field field;


    public Field getField() {
        return field;
    }

    @MCChildElement
    public void setField(Field field) {
        this.field = field;
    }
}
