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
import com.predic8.membrane.core.interceptor.oauth2.ClaimRenamer;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import com.predic8.membrane.core.interceptor.oauth2.request.NoResponse;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import org.jose4j.lang.JoseException;

public class RefreshTokenFlow extends TokenRequest {


    public RefreshTokenFlow(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        if(getRefreshToken() == null || getClientId() == null || getClientSecret() == null)
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen,"error","invalid_request");
        return new NoResponse();
    }

    @Override
    protected Response processWithParameters() throws Exception {

        if(!verifyClientThroughParams())
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen,"error","unauthorized_client");

        String username;
        try {
            username = authServer.getRefreshTokenGenerator().getUsername(getRefreshToken());
        }catch(NoSuchElementException ex){
            return OAuth2Util.createParameterizedJsonErrorResponse(exc, jsonGen,"error", "invalid_request");
        }

        params.put(ParamNames.USERNAME,username);

        try {
            authServer.getRefreshTokenGenerator().invalidateToken(getRefreshToken(), getClientId(), getClientSecret());
        }catch(NoSuchElementException ex){
            return OAuth2Util.createParameterizedJsonErrorResponse(exc, jsonGen,"error", "invalid_grant");
        }

        // TODO check if scope is "narrower" than before

        Client client;
        try {
            synchronized (authServer.getClientList()) {
                client = authServer.getClientList().getClient(getClientId());
            }
        } catch (Exception e) {
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen, "error", "invalid_client");
        }
        
        String grantTypes = client.getGrantTypes();
        if (!grantTypes.contains(getGrantType())) {
			return OAuth2Util.createParameterizedJsonErrorResponse(exc, jsonGen, "error", "invalid_grant_type");
        }
        
        scope = getScope();
        idToken = null;
        token = authServer.getTokenGenerator().getToken(getUsername(),getClientId(),getClientSecret());
        refreshToken = authServer.getRefreshTokenGenerator().getToken(getUsername(), getClientId(), getClientSecret());

        SessionManager.Session session = getSessionForAuthorizedUserWithParams();
        synchronized(session) {
            session.getUserAttributes().put(ACCESS_TOKEN, token);
        }
        authServer.getSessionFinder().addSessionForToken(token,session);
        if (OAuth2Util.isOpenIdScope(scope)) {
            idToken = createSignedIdToken(session, username, client);
        }
        
        return new NoResponse();

    }
    
    private String createSignedIdToken(SessionManager.Session session, String username, Client client) throws JoseException {
        return getSignedIdToken(username, client, getValidIdTokenClaims(session));
    }

    private String getSignedIdToken(String username, Client client, JwtGenerator.Claim... claims) throws JoseException {
        return authServer.getJwtGenerator().getSignedIdToken(authServer.getIssuer(),username,client.getClientId(),10*60,claims);
    }

    private JwtGenerator.Claim[] getValidIdTokenClaims(SessionManager.Session session){
        ClaimsParameter cp = new ClaimsParameter(authServer.getClaimList().getSupportedClaims(),session.getUserAttributes().get(ParamNames.CLAIMS));
        ArrayList<JwtGenerator.Claim> claims = new ArrayList<JwtGenerator.Claim>();
        if(cp.hasClaims()) {
            for (String claim : cp.getIdTokenClaims())
                claims.add(new JwtGenerator.Claim(claim,session.getUserAttributes().get(ClaimRenamer.convert(claim))));
        }
        return claims.toArray(new JwtGenerator.Claim[0]);
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

}
