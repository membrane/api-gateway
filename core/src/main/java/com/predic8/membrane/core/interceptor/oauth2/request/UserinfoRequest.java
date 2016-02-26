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
import com.predic8.membrane.core.interceptor.oauth2.AuthorizationHeader;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class UserinfoRequest extends ParameterizedRequest {
    private AuthorizationHeader authHeader;
    private HashMap<String, String> sessionProperties;

    public UserinfoRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        authHeader = new AuthorizationHeader(exc.getRequest());
        if (!authHeader.isSet()){
            return buildWwwAuthenticateErrorResponse( Response.badRequest(), "invalid_request");
        }

        return null;
    }

    @Override
    protected Response validateWithParameters() throws Exception {
        if(!authServer.getSessionFinder().hasSessionForToken(authHeader.getToken())) {
            return buildWwwAuthenticateErrorResponse( Response.unauthorized(), "invalid_token");
        }
        sessionProperties = new HashMap<String,String>(authServer.getSessionFinder().getSessionForToken(authHeader.getToken()).getUserAttributes());
        return null;
    }

    @Override
    protected Response getResponse() throws Exception {
        return Response
                .ok()
                .body(getJSONStringGiveMeABetterName(sessionProperties))
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .build();
    }

    protected String getJSONStringGiveMeABetterName(Map<String,String> sessionProperties) throws IOException {

        Map<String, String> scopeProperties = getScopeProperties(sessionProperties); // Do not Inline! Synchronize

        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
            gen.writeStartObject();

            for (String property : scopeProperties.keySet())
                gen.writeObjectField(property, scopeProperties.get(property));

            gen.writeEndObject();
            return jsonGen.getJson();
        }
    }

    private Map<String, String> getScopeProperties(Map<String,String> sessionProperties) {
        String[] scopes = sessionProperties.get("scope").split(" ");
        return authServer.getScopeList().getScopes(sessionProperties, scopes);
    }
}
