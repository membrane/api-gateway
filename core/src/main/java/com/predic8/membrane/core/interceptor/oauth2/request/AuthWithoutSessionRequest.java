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
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;

public class AuthWithoutSessionRequest extends ParameterizedRequest {

    public AuthWithoutSessionRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    @Override
    protected Response checkForMissingParameters() throws Exception {
        if(getClientId() == null || getRedirectUri() == null)
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        if(getResponseType() == null || getScope() == null)
            return createParameterizedFormUrlencodedRedirect(exc, getState(), getRedirectUri() + "?error=invalid_request");
        return new NoResponse();
    }

    @Override
    protected Response processWithParameters() throws Exception {
        Client client;
        try {
            client = authServer.getClientList().getClient(getClientId());
        } catch (Exception e) {
            return createParameterizedJsonErrorResponse(exc, "error", "unauthorized_client");
        }

        if (!OAuth2Util.isAbsoluteUri(getRedirectUri()) || !getRedirectUri().equals(client.getCallbackUrl()))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        if (promptEqualsNone())
            return createParameterizedFormUrlencodedRedirect(exc, getState(), client.getCallbackUrl() + "?error=login_required");


        if (!authServer.getSupportedAuthorizationGrants().contains(getResponseType()))
            return createParameterizedFormUrlencodedRedirect(exc, getState(), client.getCallbackUrl() + "?error=unsupported_response_type");

        String validScopes = verifyScopes(getScope());

        if (validScopes.isEmpty())
            return createParameterizedFormUrlencodedRedirect(exc, getState(), client.getCallbackUrl() + "?error=invalid_scope");

        if(OAuth2Util.isOpenIdScope(validScopes)) {
            if (!isCodeRequest())
                return createParameterizedFormUrlencodedRedirect(exc, getState(), client.getCallbackUrl() + "?error=invalid_request");

            //Parses the claims parameter into a json object. Claim values are always ignored and set to "null" as it is optional to react to those values
            addValidClaimsToParams();
        }else
            removeClaimsWhenNotOpenidScope();

        setScope(validScopes);

        String invalidScopes = hasGivenInvalidScopes(getScope(), validScopes);
        if (!invalidScopes.isEmpty())
            setScopeInvalid(invalidScopes);



        exc.setResponse(Response.ResponseBuilder.newInstance().build());
        addParams(createSession(exc,extractSessionId(extraxtSessionHeader(exc.getRequest()))),params);
        return new NoResponse();
    }

    private void removeClaimsWhenNotOpenidScope() {
        params.remove(ParamNames.CLAIMS);
    }

    private void addValidClaimsToParams() throws IOException {
        if(getClaims() != null) {
            ClaimsParameter claims = new ClaimsParameter(authServer.getClaimList().getSupportedClaims(), getClaims());
            if(claims.hasClaims())
                params.put(ParamNames.CLAIMS, claims.toJson());
        }
    }

    private boolean isCodeRequest() {
        return getResponseType().equals("code");
    }

    private boolean promptEqualsNone() {
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
            if (authServer.getClaimList().scopeExists(scopeList[i]))
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
