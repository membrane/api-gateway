package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.http.Response;

public class OAuth2Exception extends Exception {
    private final Response response;

    public OAuth2Exception(Response response) {
        this.response = response;
    }

    public Response getResponse() {
        return response;
    }
}
