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
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.TokenAuthorizationHeader;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class UserinfoRequest extends ParameterizedRequest {
    private TokenAuthorizationHeader authHeader;
    private HashMap<String, String> sessionProperties;

    public UserinfoRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        authHeader = new TokenAuthorizationHeader(exc.getRequest());
        if (!authHeader.isSet()){
            return buildWwwAuthenticateErrorResponse( Response.badRequest(), "invalid_request");
        }

        return new NoResponse();
    }

    @Override
    protected Response validateWithParameters() throws Exception {
        if(!authHeader.isValid() || !authServer.getSessionFinder().hasSessionForToken(authHeader.getToken())) {
            return buildWwwAuthenticateErrorResponse( Response.unauthorized(), "invalid_token");
        }
        sessionProperties = new HashMap<String,String>(authServer.getSessionFinder().getSessionForToken(authHeader.getToken()).getUserAttributes());
        return new NoResponse();
    }

    @Override
    protected Response getResponse() throws Exception {
        return Response
                .ok()
                .body(getUserDataAsJson(sessionProperties))
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .build();
    }

    protected String getUserDataAsJson(Map<String,String> sessionProperties) throws IOException {

        Map<String, String> claims = new HashMap<String, String>();
        if(OAuth2Util.isOpenIdScope(sessionProperties.get(ParamNames.SCOPE)))
            claims.putAll(getClaimsFromClaimsParameter(sessionProperties));
        claims.putAll(getClaimsFromScopes(sessionProperties));

        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
            gen.writeStartObject();

            for (String property : claims.keySet())
                gen.writeObjectField(property, claims.get(property));

            gen.writeEndObject();
            return jsonGen.getJson();
        }
    }

    private Map<String, String> getClaimsFromClaimsParameter(Map<String, String> sessionProperties) {
        ClaimsParameter cp = new ClaimsParameter(authServer.getClaimList().getSupportedClaims(),sessionProperties.get(ParamNames.CLAIMS));
        return authServer.getClaimList().getClaimsFromSession(sessionProperties, cp.getUserinfoClaims());
    }

    private Map<String, String> getClaimsFromScopes(Map<String,String> sessionProperties) {
        String[] scopes = sessionProperties.get(ParamNames.SCOPE).split(" ");
        HashSet<String> claims = new HashSet<String>();
        for(String scope : scopes){
            claims.addAll(authServer.getClaimList().getClaimsForScope(scope));
        }
        return authServer.getClaimList().getClaimsFromSession(sessionProperties, claims);
    }
}
