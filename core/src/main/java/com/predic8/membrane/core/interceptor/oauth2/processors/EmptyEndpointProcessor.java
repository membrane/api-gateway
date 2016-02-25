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
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;

public class EmptyEndpointProcessor extends ExchangeProcessor{

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

        s.authorize();
        Client client;
        String sessionRedirectUrl;
        synchronized (s) {
            client = authServer.getClientList().getClient(s.getUserAttributes().get("client_id"));
            sessionRedirectUrl = s.getUserAttributes().get("redirect_uri");
        }
        if (client.getCallbackUrl().equals(sessionRedirectUrl)) {
            if (getResponseType(s).equals("code")) {
                String code = generateAuthorizationCode();
                authCodesToSession.put(code, s);
                return respondWithAuthorizationCodeAndRedirect(exc, code);
            }
            if (getResponseType(s).equals("token")) {
                return respondWithTokenAndRedirect(exc, generateAccessToken(s, client), authServer.getTokenGenerator().getTokenType());
            }
            return createParameterizedJsonErrorResponse(exc, "error", "unsupported_response_type");
        }
        return createParameterizedJsonErrorResponse(exc, "error", "invalid_request");
    }

    protected static String getResponseType(SessionManager.Session s) {
        return s.getUserAttributes().get("response_type");
    }

    protected static String generateAuthorizationCode() {
        return new BigInteger(130, new SecureRandom()).toString(32);
    }

    protected Outcome respondWithTokenAndRedirect(Exchange exc, String token, String tokenType) {
        SessionManager.Session s = getSession(exc);
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
        extractSessionFromRequestAndAddToResponse(exc);
        return Outcome.RETURN;
    }

    private String stateQuery(String state) {
        return state == null ? "" : "&state=" + state;
    }

    protected Outcome respondWithAuthorizationCodeAndRedirect(Exchange exc, String code) throws UnsupportedEncodingException {
        SessionManager.Session s = getSession(exc);
        String state;
        String redirectUrl;
        synchronized (s) {
            state = s.getUserAttributes().get("state");
            redirectUrl = s.getUserAttributes().get("redirect_uri");
        }

        exc.setResponse(Response.
                redirect(redirectUrl + "?code=" + code + stateQuery(state), false).
                dontCache().
                body("").
                build());
        extractSessionFromRequestAndAddToResponse(exc);
        return Outcome.RETURN;
    }

    // TODO
    public static HeaderField extraxtSessionHeader(Message msg) {
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

    public Message addSessionHeader(Message msg, HeaderField session) {
        msg.getHeader().add(session);
        return msg;
    }

    public void extractSessionFromRequestAndAddToResponse(Exchange exc) {
        addSessionHeader(exc.getResponse(), extraxtSessionHeader(exc.getRequest()));
    }

    private String generateAccessToken(SessionManager.Session s, Client client) {
        synchronized (s) {
            return authServer.getTokenGenerator().getToken(s.getUserName(), client.getClientId(), client.getClientSecret());
        }
    }
}
