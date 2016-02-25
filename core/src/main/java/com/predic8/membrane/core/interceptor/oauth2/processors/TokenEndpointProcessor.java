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

package com.predic8.membrane.core.interceptor.oauth2.processors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TokenEndpointProcessor extends ExchangeProcessor {


    public TokenEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        return false;
    }

    @Override
    public Outcome process(Exchange exc) throws IOException {
        Map<String, String> params = extractBody(exc.getRequest());

        removeEmptyParams(params);

        if (!params.containsKey("code"))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        Session session;
        synchronized (authCodesToSession) {
            if (!authCodesToSession.containsKey(params.get("code"))) {
                return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");
            }
            session = authCodesToSession.get(params.get("code"));
            authCodesToSession.remove(params.get("code")); // auth codes can only be used one time
        }

        String username;

        synchronized (session) {
            username = session.getUserName();
            session.getUserAttributes().putAll(params);
        }

        if (params.get("client_id") == null || params.get("client_secret") == null)
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        Client client;
        try {
            client = authServer.getClientList().getClient(params.get("client_id"));
        } catch (Exception e) {
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_client");
        }

        if (!params.get("client_secret").equals(client.getClientSecret()))
            return createParameterizedJsonErrorResponse(exc, "error", "unauthorized_client");

        // TODO
        String givenRedirect_uri = params.get("redirect_uri");
        if (givenRedirect_uri == null || !isAbsoluteUri(givenRedirect_uri))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        String knownRedirect_uri = client.getCallbackUrl();
        if (!givenRedirect_uri.equals(knownRedirect_uri))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");


        String token = authServer.getTokenGenerator().getToken(username, client.getClientId(), client.getClientSecret());

        String idToken = null;
        if (isOpenIdScope(getScope(session))) {
            //idToken = jwtGenerator.getIdTokenSigned(...) // TODO
        }

        synchronized (tokensToSession) {
            tokensToSession.put(token, session);
        }

        session.clearCredentials();

        exc.setResponse(Response
                .ok()
                .body(getTokenJSONResponse(session, token, idToken))
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .dontCache()
                .build());

        return Outcome.RETURN;
    }

    protected Map<String, String> extractBody(Message msg) {
        String[] split = msg.getBodyAsStringDecoded().split("&");
        HashMap<String, String> params = new HashMap<String, String>();
        for (String param : split) {
            String[] paramSplit = param.split("=");
            params.put(paramSplit[0], paramSplit[1]);
        }
        return params;
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

    private String getScope(Session session) {
        synchronized (session) {
            return session.getUserAttributes().get("scope");
        }
    }

    protected String getTokenJSONResponse(Session session, String token, String idToken) throws IOException {
        String json;
        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
            gen.writeStartObject();
            gen.writeObjectField("access_token", token);
            gen.writeObjectField("token_type", authServer.getTokenGenerator().getTokenType());
            //gen.writeObjectField("expires_in", "null"); // TODO change this
            gen.writeObjectField("scope", session.getUserAttributes().get("scope"));
            if (idToken != null)
                gen.writeObjectField("id_token:", idToken);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }
        return json;
    }
}
