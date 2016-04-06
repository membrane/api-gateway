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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.request.AuthWithSessionRequest;
import com.predic8.membrane.core.interceptor.oauth2.request.AuthWithoutSessionRequest;

public class AuthEndpointProcessor extends EndpointProcessor {

    public AuthEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }


    @Override
    public boolean isResponsible(Exchange exc) {
        return exc.getRequestURI().startsWith("/oauth2/auth");
    }

    @Override
    public Outcome process(Exchange exc) throws Exception {

        SessionManager.Session s = authServer.getSessionManager().getSession(exc); // TODO: replace with getOrCreateSession() and collapse AuthWithSession and AuthWithoutSession

        if(s == null || !s.isPreAuthorized()) {
            exc.setResponse(new AuthWithoutSessionRequest(authServer,exc).validateRequest());
            return Outcome.RETURN;
        }
        exc.setResponse(new AuthWithSessionRequest(authServer,exc).validateRequest());
        return Outcome.RETURN;
    }
}
