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
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.util.ArrayList;

public class TokenRequest extends ParameterizedRequest {

    private String scope;
    private String token;
    private String idToken;

    public TokenRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        if(getCode() == null ||  getClientId() == null || getClientSecret() == null || getRedirectUri() == null)
            return createParameterizedJsonErrorResponse(exc,"error","invalid_request");
        return new NoResponse();
    }

    @Override
    protected Response validateWithParameters() throws Exception {
        if(!authServer.getSessionFinder().hasSessionForCode(getCode()))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");
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
        if (OAuth2Util.isOpenIdScope(scope)) {
            ClaimsParameter cp = new ClaimsParameter(authServer.getClaimList().getSupportedClaims(),session.getUserAttributes().get(ParamNames.CLAIMS));
            ArrayList<JwtGenerator.Claim> claims = new ArrayList<JwtGenerator.Claim>();
            if(cp.hasClaims()) {
                for (String claim : cp.getIdTokenClaims())
                    claims.add(new JwtGenerator.Claim(claim,session.getUserAttributes().get(ClaimRenamer.convert(claim))));
            }
            idToken = getSignedIdToken(username, client, claims.toArray(new JwtGenerator.Claim[0]));
        }

        authServer.getSessionFinder().addSessionForToken(token,session);
        // maybe undo this as the session is used internally
        session.clearCredentials();

        return new NoResponse();
    }

    private String getSignedIdToken(String username, Client client, JwtGenerator.Claim... claims) throws JoseException {
        return authServer.getJwtGenerator().getSignedIdToken(authServer.getIssuer(),username,client.getClientId(),10*60,claims);
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
            //gen.writeObjectField("expires_in", "null"); // TODO is optional but maybe useful?
            gen.writeObjectField(ParamNames.SCOPE, scope);
            if (idToken != null)
                gen.writeObjectField(ParamNames.ID_TOKEN, idToken);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }
        return json;
    }
}
