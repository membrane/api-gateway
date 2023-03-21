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

package com.predic8.membrane.core.interceptor.oauth2.request.tokenrequest;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import com.predic8.membrane.core.interceptor.oauth2.request.NoResponse;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import org.jose4j.lang.JoseException;

import java.util.ArrayList;

public class AuthorizationCodeFlow extends TokenRequest {

    public AuthorizationCodeFlow(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }


    @Override
    protected Response checkForMissingParameters() throws Exception {
        if(getCode() == null ||  getClientId() == null || getClientSecret() == null || getRedirectUri() == null)
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen,"error","invalid_request");
        return new NoResponse();
    }

    @Override
    protected Response processWithParameters() throws Exception {
        if(!authServer.getSessionFinder().hasSessionForCode(getCode()))
            return OAuth2Util.createParameterizedJsonErrorResponse(exc, jsonGen,"error", "invalid_request");
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
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen, "error", "invalid_client");
        }

        if(!getClientSecret().equals(client.getClientSecret()))
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen, "error", "unauthorized_client");

        if(!OAuth2Util.isAbsoluteUri(getRedirectUri()) || !getRedirectUri().equals(client.getCallbackUrl()))
            return OAuth2Util.createParameterizedJsonErrorResponse(exc, jsonGen,"error", "invalid_request");

        String grantTypes = client.getGrantTypes();
        if (!grantTypes.contains(getGrantType())) {
			return OAuth2Util.createParameterizedJsonErrorResponse(exc, jsonGen, "error", "invalid_grant_type");
        }
        
        scope = getScope(session);
        token = authServer.getTokenGenerator().getToken(username, client.getClientId(), client.getClientSecret());
        authServer.getSessionFinder().addSessionForToken(token,session);

        refreshToken = authServer.getRefreshTokenGenerator().getToken(username, client.getClientId(), client.getClientSecret());
        if (OAuth2Util.isOpenIdScope(scope)) {
            idToken = createSignedIdToken(session, username, client);
        }

        return new NoResponse();
    }

    private JwtGenerator.Claim[] getValidIdTokenClaims(SessionManager.Session session){
        ClaimsParameter cp = new ClaimsParameter(authServer.getClaimList().getSupportedClaims(),session.getUserAttributes().get(ParamNames.CLAIMS));
        ArrayList<JwtGenerator.Claim> claims = new ArrayList<>();
        if(cp.hasClaims()) {
            for (String claim : cp.getIdTokenClaims())
                claims.add(new JwtGenerator.Claim(claim,session.getUserAttributes().get(ClaimRenamer.convert(claim))));
        }
        return claims.toArray(new JwtGenerator.Claim[0]);
    }

    private String createSignedIdToken(SessionManager.Session session, String username, Client client) throws JoseException {
        return getSignedIdToken(username, client, getValidIdTokenClaims(session));
    }

    private String getSignedIdToken(String username, Client client, JwtGenerator.Claim... claims) throws JoseException {
        return authServer.getJwtGenerator().getSignedIdToken(authServer.getIssuer(),username,client.getClientId(),10*60,claims);
    }

    @Override
    protected Response getResponse() throws Exception {
        return Response
                .ok()
                .body(getTokenJSONResponse())
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .dontCache()
                .build();
    }

    private String getScope(SessionManager.Session session) {
        synchronized (session) {
            return session.getUserAttributes().get("scope");
        }
    }
}
