package com.predic8.membrane.core.openapi.model;

import com.predic8.membrane.core.openapi.model.*;

public class StringBody implements Body {

    private String payload;

    public StringBody(String s) {
        payload=s;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public String asString() {
        return payload;
    }
}
