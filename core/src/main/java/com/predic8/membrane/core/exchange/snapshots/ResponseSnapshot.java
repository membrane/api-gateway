package com.predic8.membrane.core.exchange.snapshots;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;

public class ResponseSnapshot extends MessageSnapshot {

    int statusCode;
    String statusMessage;

    public ResponseSnapshot(Response response) {
        super(response);
        this.statusCode = response.getStatusCode();
        this.statusMessage = response.getStatusMessage();
    }

    public ResponseSnapshot() {
        super();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Response toResponse() {
        Response result = new Response();
        result.setHeader(convertHeader());
        result.setBody(convertBody());
        result.setStatusCode(getStatusCode());
        result.setStatusMessage(getStatusMessage());
        return result;
    }
}
