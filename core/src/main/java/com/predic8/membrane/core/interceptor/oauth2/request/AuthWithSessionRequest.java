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

package com.predic8.membrane.core.interceptor.oauth2.request;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;

public class AuthWithSessionRequest extends ParameterizedRequest{

    public AuthWithSessionRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        if (getPrompt() == null)
            return OAuth2Util.createParameterizedJsonErrorResponse(exc, jsonGen, "error", "invalid_request");

        return new NoResponse();
    }

    @Override
    protected Response processWithParameters() throws Exception {
        if(getPrompt().equals("login"))
            return clearSessionAndRedirectToAuthEndpoint(exc);

        if(getPrompt().equals("none") && !getSession(exc).isAuthorized())
            return OAuth2Util.createParameterizedJsonErrorResponse(exc,jsonGen, "error","login_required");
        return new NoResponse();
    }

    @Override
    protected Response getResponse() throws Exception {
        return OAuth2Util.createParameterizedJsonErrorResponse(exc, jsonGen, "error", "login_required");
    }

    private Response clearSessionAndRedirectToAuthEndpoint(Exchange exc){
        SessionManager.Session session = getSession(exc);
        session.clear();
        return redirectToOAuth2AuthEndpoint(exc);
    }

    private static Response redirectToOAuth2AuthEndpoint(Exchange exc) {
        return Response.redirect(exc.getRequestURI(), false).dontCache().bodyEmpty().build();
    }
}
