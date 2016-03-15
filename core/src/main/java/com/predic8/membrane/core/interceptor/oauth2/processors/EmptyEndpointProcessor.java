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
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.flows.CodeFlow;
import com.predic8.membrane.core.interceptor.oauth2.flows.TokenFlow;

public class EmptyEndpointProcessor extends EndpointProcessor {

    public EmptyEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        SessionManager.Session s = getSession(exc);
        return exc.getRequestURI().equals("/") && s != null && s.isPreAuthorized();
    }

    @Override
    public Outcome process(Exchange exc) throws Exception {
        SessionManager.Session s = getSession(exc);
        if(!OAuth2Util.isOpenIdScope(s.getUserAttributes().get(ParamNames.SCOPE)))
            s.getUserAttributes().put("consent","true");
        if(!s.getUserAttributes().containsKey("consent")){
            return redirectToConsentPage(exc);
        }
        if(s.getUserAttributes().get("consent").equals("true")) {
            s.authorize();
            if (getResponseType(s).equals("code")) {
                return new CodeFlow(authServer, exc, s).getResponse();

            }
            if (getResponseType(s).equals("token"))
                return new TokenFlow(authServer, exc, s).getResponse();
            return createParameterizedJsonErrorResponse(exc, "error", "unsupported_response_type");
        }
        return createParameterizedJsonErrorResponse(exc, "error", "consent_required");
    }

    private Outcome redirectToConsentPage(Exchange exc) {
        exc.setResponse(Response.redirect("/login/consent",false).dontCache().bodyEmpty().build());
        return Outcome.RETURN;
    }

    protected static String getResponseType(SessionManager.Session s) {
        synchronized(s) {
            return s.getUserAttributes().get("response_type");
        }
    }










}
