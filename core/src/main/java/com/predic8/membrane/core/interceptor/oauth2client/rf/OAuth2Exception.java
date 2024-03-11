package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.http.Response;

public class OAuth2Exception extends Exception {
    private final String error;
    private final String errorDescription;
    private final Response response;

    public OAuth2Exception(String error, String errorDescription, Response response) {
        this.error = error;
        this.errorDescription = errorDescription;
        this.response = response;
    }

    public String getError() {
        return error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public Response getResponse() {
        return response;
    }
}
