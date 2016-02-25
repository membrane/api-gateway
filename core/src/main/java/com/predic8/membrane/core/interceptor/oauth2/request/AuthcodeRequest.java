/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.util.URLParamUtil;

import java.io.IOException;
import java.util.Map;

public class AuthcodeRequest extends ParameterizedRequest {

    public AuthcodeRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected void checkForMissingParameters() throws IOException {
        if(!params.containsKey("client_id"))
            requestResponse = createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        Client client;
        try {
            client = authServer.getClientList().getClient(params.get("client_id"));
        } catch (Exception e) {
            requestResponse =  createParameterizedJsonErrorResponse(exc, "error", "unauthorized_client");
            return;
        }

        if(!params.containsKey("redirect_uri") || !isAbsoluteUri(params.get("redirect_uri")) || !params.get("redirect_uri").equals(client.getCallbackUrl()))
            requestResponse = createParameterizedJsonErrorResponse(exc, "error", "invalid_request");


    }

    @Override
    protected void validateParameters() {
        if(requestResponse != null)
            return;

    }

    @Override
    protected Response getResponse() {
        if(requestResponse != null)
            return requestResponse;

        //TODO create accepted response
        return null;
    }



    protected boolean isAbsoluteUri(String givenRedirect_uri) {
        try {
            // Doing it this way as URIs scheme seems to be wrong
            String[] split = givenRedirect_uri.split("://");
            return split.length == 2;
        } catch (Exception ignored) {
            return false;
        }
    }
}
