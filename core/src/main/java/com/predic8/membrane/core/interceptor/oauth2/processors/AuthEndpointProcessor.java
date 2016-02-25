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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.util.URLParamUtil;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

public class AuthEndpointProcessor extends ExchangeProcessor {

    public AuthEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }


    @Override
    public boolean isResponsible(Exchange exc) {
        return exc.getRequestURI().startsWith("/oauth2/auth");
    }

    @Override
    public Outcome process(Exchange exc) throws Exception {

        if(getSession(exc) == null) {
            return processWithoutSession(exc);
        }
        return processWithSession(exc);
    }

    private Outcome processWithSession(Exchange exc) throws Exception {
        Map<String, String> params = getValidParams(exc);

        if (!params.containsKey("prompt"))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        SessionManager.Session session = getSession(exc);

        String prompt = params.get("prompt");
        if (prompt.equals("login")) {
            session.clear();
            return redirectToOAuth2AuthEndpoint(exc);
        }

        //TODO prüfen
        if (prompt.equals("none") && !session.isAuthorized()) {
            return createParameterizedJsonErrorResponse(exc, "error", "login_required");
        }
        return createParameterizedJsonErrorResponse(exc, "error", "login_required");
    }

    private Map<String, String> getValidParams(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);
        removeEmptyParams(params);
        return params;
    }

    private Outcome processWithoutSession( Exchange exc) throws Exception {

        Map<String, String> params = getValidParams(exc);

        String givenState = params.get("state");

        String givenClientId = params.get("client_id");
        if (givenClientId == null)
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        Client client;
        try {
            client = authServer.getClientList().getClient(givenClientId);
        } catch (Exception e) {
            return createParameterizedJsonErrorResponse(exc, "error", "unauthorized_client");
        }

        // TODO new Object for given...

        String givenRedirect_uri = params.get("redirect_uri");
        if (givenRedirect_uri == null)
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        if (!isAbsoluteUri(givenRedirect_uri))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        String knownRedirect_uri = client.getCallbackUrl();
        if (!givenRedirect_uri.equals(knownRedirect_uri))
            return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");

        //openid specific
        if (params.containsKey("prompt")) {
            String prompt = params.get("prompt");
            if (prompt.equals("none"))
                return createParameterizedFormUrlencodedRedirect(exc, givenState, knownRedirect_uri + "?error=login_required");
        }

        SessionManager.Session s;

        exc.setResponse(new Response.ResponseBuilder().build());
        synchronized (authServer.getSessionManager()) {
            s = authServer.getSessionManager().createSession(exc, extractSessionId(extraxtSessionHeader(exc.getRequest())));
        }

        String givenAuthorizationGrant = params.get("response_type");
        if (givenAuthorizationGrant == null)
            return createParameterizedFormUrlencodedRedirect(exc, givenState, knownRedirect_uri + "?error=invalid_request");

        if (!supportedAuthorizationGrants.contains(givenAuthorizationGrant))
            return createParameterizedFormUrlencodedRedirect(exc, givenState, knownRedirect_uri + "?error=unsupported_response_type");


        String givenScopes = params.get("scope");
        if (givenScopes == null)
            return createParameterizedFormUrlencodedRedirect(exc, givenState, knownRedirect_uri + "?error=invalid_request");

        String validScopes = verifyScopes(givenScopes);

        if (validScopes.isEmpty())
            return createParameterizedFormUrlencodedRedirect(exc, givenState, knownRedirect_uri + "?error=invalid_scope");

        params.put("scope", validScopes);

        String invalidScopes = hasGivenInvalidScopes(givenScopes, validScopes);
        if (!invalidScopes.isEmpty())
            params.put("scope_invalid", invalidScopes);
        synchronized (s) {
            s.getUserAttributes().putAll(params);
        }
        return redirectToLoginWithSession(exc, extraxtSessionHeader(exc.getResponse()));
    }

    public Outcome createParameterizedFormUrlencodedRedirect(Exchange exc, String state, String url) {
        if (state != null)
            url += "&state=" + state;
        exc.setResponse(Response.redirect(url, false).header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded").bodyEmpty().dontCache().build());
        return Outcome.RETURN;
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

    // TODO
    protected HeaderField extraxtSessionHeader(Message msg) {
        for (HeaderField h : msg.getHeader().getAllHeaderFields()) {
            if (h.getHeaderName().equals("Set-Cookie")) {
                return h;
            } else if (h.getHeaderName().equals("Cookie")) {
                h.setHeaderName(new HeaderName("Set-Cookie"));
                return h;
            }
        }
        throw new RuntimeException();
    }

    protected static String extractSessionId(HeaderField sessionHeader) {
        for (String s : sessionHeader.getValue().split(" ")) {
            if (s.startsWith("SESSIONID=")) {
                return s.substring(10);
            }
        }
        throw new RuntimeException("SessionId not found in Session header!");
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

    protected Outcome redirectToLoginWithSession(Exchange exc, HeaderField session) throws MalformedURLException, UnsupportedEncodingException {
        exc.setResponse(Response.
                redirect(authServer.getPath(), false).
                dontCache().
                body("").
                build());
        addSessionHeader(exc.getResponse(), session);
        return Outcome.RETURN;
    }

    public static Message addSessionHeader(Message msg, HeaderField session) {
        msg.getHeader().add(session);
        return msg;
    }

    private static Outcome redirectToOAuth2AuthEndpoint(Exchange exc) {
        exc.setResponse(Response.redirect(exc.getRequestURI(), false).dontCache().bodyEmpty().build());
        return Outcome.RETURN;
    }
}
