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
import com.predic8.membrane.core.interceptor.oauth2.request.NoResponse;
import com.predic8.membrane.core.interceptor.oauth2.request.ParameterizedRequest;

import java.io.IOException;

public class PasswordFlow extends TokenRequest {

    public PasswordFlow(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        if(getGrantType() == null || getUsername() == null || getPassword() == null || getClientId() == null || getClientSecret() == null)
            return createParameterizedJsonErrorResponse(exc,"error","invalid_request");
        return new NoResponse();
    }

    @Override
    protected Response processWithParameters() throws Exception {

        if(!verifyClientThroughParams())
            return createParameterizedJsonErrorResponse(exc,"error","unauthorized_client");

        if(!verifyUserThroughParams())
            return createParameterizedJsonErrorResponse(exc,"error","access_denied");


        token = createTokenForVerifiedUserAndClient();

        scope = getScope() == null ? "" : getScope();
        idToken = null;
        token = createTokenForVerifiedUserAndClient();

        exc.setResponse(getEarlyResponse());

        SessionManager.Session session = createSessionForAuthorizedUserWithParams();
        session.getUserAttributes().put(ACCESS_TOKEN, token);
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
                .body(getTokenJSONResponse(scope, token, idToken))
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .dontCache()
                .build();
    }
}
