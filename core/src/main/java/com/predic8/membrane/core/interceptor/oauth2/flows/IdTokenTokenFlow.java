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

package com.predic8.membrane.core.interceptor.oauth2.flows;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.ClaimRenamer;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import org.jose4j.lang.JoseException;

import java.util.ArrayList;

public class IdTokenTokenFlow extends OAuth2Flow {

    Client client = null;
    String username = null;
    String token = null;
    String idToken = null;
    TokenFlow tokenFlow = null;

    public IdTokenTokenFlow(OAuth2AuthorizationServerInterceptor authServer, Exchange exc, SessionManager.Session s) throws JoseException {
        super(authServer, exc, s);
        client = authServer.getClientList().getClient(session.getUserAttributes().get("client_id"));
        username = s.getUserName();

        tokenFlow = new TokenFlow(authServer,exc,session);
        token = tokenFlow.generateAccessToken(client);
        idToken = createSignedIdToken();
    }

    @Override
    public Outcome getResponse() throws Exception {
        return respondWithTokensAndRedirect();
    }

    private Outcome respondWithTokensAndRedirect() {
        tokenFlow.getResponse();

        addIdTokenToRedirect();

        return Outcome.RETURN;
    }

    private void addIdTokenToRedirect() {
        exc.getResponse().getHeader().setValue("Location",exc.getResponse().getHeader().getFirstValue("Location")+ "&id_token="+idToken);
    }

    private JwtGenerator.Claim[] getValidIdTokenClaims(){
        ClaimsParameter cp = new ClaimsParameter(authServer.getClaimList().getSupportedClaims(),session.getUserAttributes().get(ParamNames.CLAIMS));
        ArrayList<JwtGenerator.Claim> claims = new ArrayList<JwtGenerator.Claim>();
        if(cp.hasClaims()) {
            for (String claim : cp.getIdTokenClaims())
                claims.add(new JwtGenerator.Claim(claim,session.getUserAttributes().get(ClaimRenamer.convert(claim))));
        }
        return claims.toArray(new JwtGenerator.Claim[0]);
    }

    private String createSignedIdToken() throws JoseException {
        return getSignedIdToken(getValidIdTokenClaims());
    }

    private String getSignedIdToken(JwtGenerator.Claim... claims) throws JoseException {
        return authServer.getJwtGenerator().getSignedIdToken(authServer.getIssuer(),username,client.getClientId(),10*60,claims);
    }
}
