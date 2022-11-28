package com.predic8.membrane.core.openapi.model;

public class Message {

    protected Body body = new NoBody();

    public Body getBody() {
        return body;
    }

    public Request body(String s) {
        return null;
    }
}
