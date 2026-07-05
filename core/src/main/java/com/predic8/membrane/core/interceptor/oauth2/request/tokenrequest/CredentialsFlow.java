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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.stream;

public class CredentialsFlow extends TokenRequest {

    private static final Logger log = LoggerFactory.getLogger(CredentialsFlow.class);

    public CredentialsFlow(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        // TODO also check for client id and client secret and additionally for the username ( else we cant create tokens )
        if(getGrantType() == null || getClientId() == null || getClientSecret() == null)
            return OAuth2Util.createParameterizedJsonErrorResponse("error","invalid_request");
        return new NoResponse();
    }

    @Override
    protected Response processWithParameters() throws Exception {
        if(!verifyClientThroughParams())
            return OAuth2Util.createParameterizedJsonErrorResponse("error","unauthorized_client");

        Client client;
        try {
            synchronized (authServer.getClientList()) {
                client = authServer.getClientList().getClient(getClientId());
            }
        } catch (Exception e) {
            return OAuth2Util.createParameterizedJsonErrorResponse("error", "invalid_client");
        }

        String grantTypes = client.getGrantTypes();
        if (!grantTypes.contains(getGrantType())) {
            log.info("Invalid grant type: {}", getGrantType());
			return OAuth2Util.createParameterizedJsonErrorResponse("error", "invalid_grant_type");
        }

        if (!requestedResourcesAllowed(client)) {
            log.info("Invalid target: {}", getResource());
            return OAuth2Util.createParameterizedJsonErrorResponse("error", "invalid_target");
        }

        if (!requestedScopesAllowed(client)) {
            log.info("Invalid scope: {}",getScope());
            return OAuth2Util.createParameterizedJsonErrorResponse("error", "invalid_scope");
        }

        String[] audiences = getAudiences(client);
        List<String> grantedScopes = getGrantedScopes(client);
        scope = grantedScopes.isEmpty() ? getScope() : String.join(" ", grantedScopes);

        token = createTokenForVerifiedClient(tokenClaims("aud", "scope", audiences, grantedScopes));
        expiration = authServer.getTokenGenerator().getExpiration();

        SessionManager.Session session = createSessionForAuthorizedClientWithParams();
        synchronized(session) {
            session.getUserAttributes().put(ACCESS_TOKEN, token);
        }

        authServer.getSessionFinder().addSessionForToken(token,session);

        if (authServer.isIssueNonSpecRefreshTokens()) {
            refreshToken = authServer.getRefreshTokenGenerator().getToken(client.getClientId(), client.getClientId(), client.getClientSecret(), tokenClaims("i-aud", "i-scope", audiences, grantedScopes));
            authServer.getSessionFinder().addSessionForRefreshToken(refreshToken, session);
        }

        if (authServer.isIssueNonSpecIdTokens() && OAuth2Util.isOpenIdScope(scope))
            idToken = createSignedIdToken(session, client.getClientId(), client);

        exc.setResponse(getEarlyResponse());

        return new NoResponse();
    }

    private boolean requestedResourcesAllowed(Client client) {
        String requested = getResource();
        if (requested == null)
            return true;
        List<String> allowed = client.getResourceList();
        return stream(requested.trim().split(" +"))
                .allMatch(resource -> isValidResourceUri(resource) && allowed.contains(resource));
    }

    private String[] getAudiences(Client client) {
        String requested = getResource();
        if (requested != null)
            return requested.trim().split(" +");
        return client.getResourceList().toArray(new String[0]);
    }

    /**
     * A "scope" request parameter is only validated if the client has a scope allowlist;
     * without one, it is passed through unchecked to preserve the previous behavior.
     */
    private boolean requestedScopesAllowed(Client client) {
        String requested = getScope();
        if (requested == null)
            return true;
        List<String> allowed = client.getScopeList();
        if (allowed.isEmpty())
            return true;
        return stream(requested.trim().split(" +")).allMatch(allowed::contains);
    }

    private List<String> getGrantedScopes(Client client) {
        List<String> allowed = client.getScopeList();
        if (allowed.isEmpty())
            return List.of();
        String requested = getScope();
        if (requested == null)
            return allowed;
        return List.of(requested.trim().split(" +"));
    }

    private Map<String, Object> tokenClaims(String audClaimName, String scopeClaimName, String[] audiences, List<String> grantedScopes) {
        Map<String, Object> claims = new HashMap<>(audClaims(audClaimName, audiences));
        if (!grantedScopes.isEmpty())
            claims.put(scopeClaimName, String.join(" ", grantedScopes));
        return claims;
    }

    private Map<String, Object> audClaims(String claimName, String[] audiences) {
        if (audiences.length == 0)
            return Map.of();
        if (audiences.length == 1)
            return Map.of(claimName, audiences[0]);
        return Map.of(claimName, audiences);
    }

    private static boolean isValidResourceUri(String resource) {
        try {
            URI uri = new URI(resource);
            return uri.isAbsolute() && uri.getFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
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
        return exc.getResponse();
    }

    private Response getEarlyResponse() throws IOException {
        return Response
                .ok()
                .body(getTokenJSONResponse())
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .dontCache()
                .build();
    }
}
