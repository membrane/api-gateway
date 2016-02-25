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

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.AuthorizationHeader;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;

import java.io.IOException;
import java.util.Map;

public class UserinfoEndpointProcessor extends ExchangeProcessor {


    public UserinfoEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        return exc.getRequestURI().startsWith("/oauth2/userinfo");
    }

    @Override
    public Outcome process(Exchange exc) throws IOException {
        AuthorizationHeader authHeader = new AuthorizationHeader(exc.getRequest());

        if (!authHeader.isSet()){
            exc.setResponse(buildWwwAuthenticateErrorResponse( Response.badRequest(), "invalid_request"));
            return Outcome.RETURN;
        }

        if (!tokensToSession.containsKey(authHeader.getToken())) {
            exc.setResponse(buildWwwAuthenticateErrorResponse( Response.unauthorized(), "invalid_token"));
            return Outcome.RETURN;
        }

        SessionManager.Session session;
        synchronized (tokensToSession) {
            session = tokensToSession.get(authHeader.getToken());
        }

        exc.setResponse(Response
                .ok()
                .body(getJSONStringGiveMeABetterName(session))
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .build());

        return Outcome.RETURN;
    }

    private Response buildWwwAuthenticateErrorResponse(Response.ResponseBuilder builder, String errorValue) {
        return builder.bodyEmpty().header(Header.WWW_AUTHENTICATE, authServer.getTokenGenerator().getTokenType() + " error=\""+errorValue+"\"").build();
    }

    protected String getJSONStringGiveMeABetterName(SessionManager.Session session) throws IOException {

        Map<String, String> scopeProperties = getScopeProperties(session); // Do not Inline! Synchronize

        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
            gen.writeStartObject();

            for (String property : scopeProperties.keySet())
                gen.writeObjectField(property, scopeProperties.get(property));

            gen.writeEndObject();
            return jsonGen.getJson();
        }
    }

    private Map<String, String> getScopeProperties(SessionManager.Session session) {
        synchronized (session) {
            String[] scopes = session.getUserAttributes().get("scope").split(" ");
            return authServer.getScopeList().getScopes(session.getUserAttributes(), scopes);
        }
    }
}
