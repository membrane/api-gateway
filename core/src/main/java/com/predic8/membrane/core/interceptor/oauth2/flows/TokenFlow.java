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
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;

public class TokenFlow extends OAuth2Flow {
    public TokenFlow(OAuth2AuthorizationServerInterceptor authServer, Exchange exc, SessionManager.Session s) {
        super(authServer, exc, s);
    }

    @Override
    public Outcome getResponse() {
        Client client;
        synchronized (session) {
            client = authServer.getClientList().getClient(session.getUserAttributes().get("client_id"));
        }
        return respondWithTokenAndRedirect(exc, generateAccessToken(client), authServer.getTokenGenerator().getTokenType(),session);
    }

    private Outcome respondWithTokenAndRedirect(Exchange exc, String token, String tokenType, SessionManager.Session s) {
        String state;
        String redirectUrl;
        String scope;
        synchronized (s) {
            state = s.getUserAttributes().get("state");
            redirectUrl = s.getUserAttributes().get("redirect_uri");
            scope = s.getUserAttributes().get("scope");
        }

        exc.setResponse(Response.
                redirect(redirectUrl + "?access_token=" + token + stateQuery(state) + "&token_type=" + tokenType + "&scope=" + scope, false).
                dontCache().
                body("").
                build());
        OAuth2Util.extractSessionFromRequestAndAddToResponse(exc);
        return Outcome.RETURN;
    }

    public String generateAccessToken(Client client) {
        synchronized (session) {
            String token = authServer.getTokenGenerator().getToken(session.getUserName(), client.getClientId(), client.getClientSecret());
            authServer.getSessionFinder().addSessionForToken(token,session);
            return token;
        }
    }
}
