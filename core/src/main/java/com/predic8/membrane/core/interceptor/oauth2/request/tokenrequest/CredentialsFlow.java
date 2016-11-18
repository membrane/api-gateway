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
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;
import com.predic8.membrane.core.interceptor.oauth2.request.NoResponse;

import java.io.IOException;

public class CredentialsFlow extends TokenRequest {

    public CredentialsFlow(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        // TODO also check for client id and client secret and additionally for the username ( else we cant create tokens )
        if(getGrantType() == null || getClientId() == null || getClientSecret() == null)
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen,"error","invalid_request");
        return new NoResponse();
    }

    @Override
    protected Response processWithParameters() throws Exception {
        if(!verifyClientThroughParams())
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen,"error","unauthorized_client");

        scope = getScope();
        idToken = null;
        token = createTokenForVerifiedClient();

        exc.setResponse(getEarlyResponse());

        SessionManager.Session session = createSessionForAuthorizedClientWithParams();
        synchronized(session) {
            session.getUserAttributes().put(ACCESS_TOKEN, token);
        }
        authServer.getSessionFinder().addSessionForToken(token,session);

        return new NoResponse();
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
