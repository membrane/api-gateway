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
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.util.URLParamUtil;

import java.util.Map;

public class RevocationEndpointProcessor extends EndpointProcessor {

    public RevocationEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        return exc.getRequestURI().startsWith("/oauth2/revoke");
    }

    @Override
    public Outcome process(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);

        if (!params.containsKey("token") || !params.containsKey("client_id") ||!params.containsKey("client_secret"))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        SessionManager.Session session = authServer.getSessionFinder().getSessionForToken(params.get("token"));
        if (session == null) { // token doesnt exist -> token is already invalid
            exc.setResponse(Response
                    .ok()
                    .bodyEmpty()
                    .build());
            return Outcome.RETURN;
        }

        try {
            authServer.getTokenGenerator().invalidateToken(params.get("token"), params.get("client_id"), params.get("client_secret"));
        } catch (Exception e) {
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_grant");
        }
        session.clear();
        exc.setResponse(Response
                .ok()
                .bodyEmpty()
                .build());
        return Outcome.RETURN;
    }
}
