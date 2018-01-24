package com.predic8.membrane.core.exchange.snapshots;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;

public class RequestSnapshot extends MessageSnapshot {

    String method;
    String uri;

    public RequestSnapshot(Request request) {
        super(request);
        this.method = request.getMethod();
        this.uri = request.getUri();
    }

    public RequestSnapshot() {
        super();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
