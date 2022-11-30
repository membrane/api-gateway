package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.util.*;

import java.io.*;

public class Message<T> {

    protected Body body = new NoBody();
    protected String mediaType;

    public Body getBody() {
        return body;
    }

    public T body(Body body) {
        this.body = body;
        if (body instanceof JsonBody)
            this.mediaType("application/json");
        return (T) this;
    }

    public T body(InputStream inputStream) {
        this.body = new InputStreamBody(inputStream);
        return (T) this;
    }

    public T body(String s) {
        this.body = new StringBody(s);
        return (T) this;
    }

    public T body(JsonNode n) {
        this.body = new JsonBody(n);
        this.mediaType("application/json");
        return (T) this;
    }

//    public Message body(String s) {
//        return null;
//    }

    public T mediaType(String mediaType) {
        this.mediaType = Utils.getMediaTypeFromContentTypeHeader(mediaType);
        return (T) this;
    }

    public boolean hasBody() {
        if (body == null)
            return false;
        return !(body instanceof NoBody);
    }

    public T json() {
        this.mediaType("application/json");
        return (T) this;
    }

    public String getMediaType() {
        return mediaType;
    }
}
