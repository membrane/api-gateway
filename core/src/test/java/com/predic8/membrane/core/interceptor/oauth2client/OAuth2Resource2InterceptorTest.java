/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2Exception;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;

import static com.predic8.membrane.core.http.Response.internalServerError;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2Resource2InterceptorTest {

    private OAuth2Resource2Interceptor oauth2;
    private AuthorizationService auth;

    @BeforeEach
    void setup() throws Exception {
        oauth2 = new OAuth2Resource2Interceptor();

        auth = mock(AuthorizationService.class);
        oauth2.setAuthService(auth);

        oauth2.setLogoutUrl("/login/logout");
        oauth2.setAfterLogoutUrl("/uebersicht");
        oauth2.setAppendAccessTokenToRequest(true);

        oauth2.init(new TestRouter());
    }

    @Test
    void login() throws Exception {
        var exc = new Request.Builder()
                .get("/login")
                .buildExchange();
        exc.setOriginalRequestUri("/login");

        oauth2.handleRequest(exc);
    }

    @Test
    void logout() throws Exception {
        var exc = new Request.Builder()
                .get("/login/logout")
                .buildExchange();
        exc.setOriginalRequestUri("/login/logout");

        oauth2.handleRequestInternal(exc);
    }

    @Test
    void unreachableAuthorizationServerKeepsTheSession() throws Exception {
        when(auth.refreshTokenRequest(any(), any(), anyString())).thenThrow(communicationError());

        Session session = expiredButAuthenticatedSession();
        Exchange exc = requestWith(session);

        oauth2.handleRequestInternal(exc);

        assertTrue(session.isVerified(), "an unreachable authorization server logged the user out");
        assertEquals("alice", session.getUsername());
        assertFalse(session.get().isEmpty(), "the session was cleared");
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    private static OAuth2Exception communicationError() {
        return new OAuth2Exception(
                MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR,
                MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION,
                internalServerError().body(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION).build());
    }

    private static Exchange requestWith(Session session) throws Exception {
        Exchange exc = new Request.Builder().get("/foo").buildExchange();
        exc.setOriginalRequestUri("/foo");
        exc.setProperty(SessionManager.SESSION, session);
        return exc;
    }

    private static Session expiredButAuthenticatedSession() throws Exception {
        OAuth2AnswerParameters params = new OAuth2AnswerParameters();
        params.setAccessToken("expired-access-token");
        params.setRefreshToken("the-refresh-token");
        params.setExpiration("60");
        params.setReceivedAt(LocalDateTime.now().minusHours(1));

        Session session = new Session("username", new HashMap<>());
        session.setOAuth2Answer(params.serialize());
        session.authorize("alice");
        return session;
    }
}
