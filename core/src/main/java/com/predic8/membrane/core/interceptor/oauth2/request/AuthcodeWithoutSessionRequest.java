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
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;

public class AuthcodeWithoutSessionRequest extends ParameterizedRequest {

    public AuthcodeWithoutSessionRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        if(getClientId() == null || getRedirectUri() == null)
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        if(getResponseType() == null || getScope() == null)
            return createParameterizedFormUrlencodedRedirect(exc, getState(), getRedirectUri() + "?error=invalid_request");
        return null;
    }

    @Override
    protected Response validateWithParameters() throws Exception {
        Client client;
        try {
            client = authServer.getClientList().getClient(getClientId());
        } catch (Exception e) {
            return createParameterizedJsonErrorResponse(exc, "error", "unauthorized_client");
        }

        if (!isAbsoluteUri(getRedirectUri()) || !getRedirectUri().equals(client.getCallbackUrl()))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        if (noPrompt())
            return createParameterizedFormUrlencodedRedirect(exc, getState(), client.getCallbackUrl() + "?error=login_required");


        if (!authServer.getSupportedAuthorizationGrants().contains(getResponseType()))
            return createParameterizedFormUrlencodedRedirect(exc, getState(), client.getCallbackUrl() + "?error=unsupported_response_type");

        String validScopes = verifyScopes(getScope());

        if (validScopes.isEmpty())
            return createParameterizedFormUrlencodedRedirect(exc, getState(), client.getCallbackUrl() + "?error=invalid_scope");

        setScope(validScopes);

        String invalidScopes = hasGivenInvalidScopes(getScope(), validScopes);
        if (!invalidScopes.isEmpty())
            setScopeInvalid(invalidScopes);

        exc.setResponse(Response.ResponseBuilder.newInstance().build());
        addParams(createSession(exc,extractSessionId(extraxtSessionHeader(exc.getRequest()))),params);
        return null;
    }

    private boolean noPrompt() {
        return getPrompt() != null && getPrompt().equals("none");
    }

    @Override
    protected Response getResponse() throws Exception {
        return redirectToLoginWithSession(exc, extraxtSessionHeader(exc.getResponse()));
    }

    protected String verifyScopes(String scopes) {
        String[] scopeList = scopes.split(" ");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < scopeList.length; i++) {
            if (authServer.getScopeList().scopeExists(scopeList[i]))
                builder.append(scopeList[i]).append(" ");
        }
        return builder.toString().trim();
    }

    protected String hasGivenInvalidScopes(String givenScopes, String validScopes) {

        HashSet<String> valid = new HashSet<String>(Arrays.asList(validScopes.split(" ")));

        StringBuilder builder = new StringBuilder();

        for (String scope : new HashSet<String>(Arrays.asList(givenScopes.split(" ")))) {
            if (!valid.contains(scope))
                builder.append(scope).append(" ");
        }
        return builder.toString().trim();
    }

    protected Response redirectToLoginWithSession(Exchange exc, HeaderField session) throws MalformedURLException, UnsupportedEncodingException {
        Response resp = Response.
                redirect(authServer.getPath(), false).
                dontCache().
                body("").
                build();
        addSessionHeader(resp, session);
        return resp;
    }

    public static Message addSessionHeader(Message msg, HeaderField session) {
        msg.getHeader().add(session);
        return msg;
    }
}
