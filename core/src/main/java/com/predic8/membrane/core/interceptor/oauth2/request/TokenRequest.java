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
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;

import java.io.IOException;

public class TokenRequest extends ParameterizedRequest {

    private String scope;
    private String token;
    private String idToken;

    public TokenRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        if(getCode() == null || !authServer.getSessionFinder().hasSessionForCode(getCode()) || getClientId() == null || getClientSecret() == null || getRedirectUri() == null)
            return createParameterizedJsonErrorResponse(exc,"error","invalid_request");
        return null;
    }

    @Override
    protected Response validateWithParameters() throws Exception {
        SessionManager.Session session = authServer.getSessionFinder().getSessionForCode(getCode());
        authServer.getSessionFinder().removeSessionForCode(getCode());

        String username;
        synchronized (session) {
            username = session.getUserName();
            session.getUserAttributes().putAll(params);
        }
        Client client;
        try {
            synchronized (authServer.getClientList()) {
                client = authServer.getClientList().getClient(getClientId());
            }
        } catch (Exception e) {
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_client");
        }

        if(!getClientSecret().equals(client.getClientSecret()))
            return createParameterizedJsonErrorResponse(exc, "error", "unauthorized_client");

        if(!isAbsoluteUri(getRedirectUri()) || !getRedirectUri().equals(client.getCallbackUrl()))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        scope = getScope(session);
        token = authServer.getTokenGenerator().getToken(username, client.getClientId(), client.getClientSecret());
        idToken = null;
        if (isOpenIdScope(scope)) {
            //idToken = jwtGenerator.getIdTokenSigned(...) // TODO
        }

        authServer.getSessionFinder().addSessionForToken(token,session);
        // maybe undo this as the session is used internally
        session.clearCredentials();

        return null;
    }

    @Override
    protected Response getResponse() throws Exception {
        return Response
                .ok()
                .body(getTokenJSONResponse(scope, token, idToken))
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .dontCache()
                .build();
    }

    private boolean isOpenIdScope(String scope) {
        if (scope.contains("openid")) {
            String[] split = scope.split(" ");
            for (String singleScope : split)
                if (singleScope.equals("openid"))
                    return true;
        }
        return false;
    }

    private String getScope(SessionManager.Session session) {
        synchronized (session) {
            return session.getUserAttributes().get("scope");
        }
    }

    protected String getTokenJSONResponse(String scope, String token, String idToken) throws IOException {
        String json;
        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
            gen.writeStartObject();
            gen.writeObjectField("access_token", token);
            gen.writeObjectField("token_type", authServer.getTokenGenerator().getTokenType());
            //gen.writeObjectField("expires_in", "null"); // TODO change this
            gen.writeObjectField("scope", scope);
            if (idToken != null)
                gen.writeObjectField("id_token:", idToken);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }
        return json;
    }
}
