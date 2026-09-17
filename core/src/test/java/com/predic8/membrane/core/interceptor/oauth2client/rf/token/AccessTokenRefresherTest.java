/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2Exception;
import com.predic8.membrane.core.interceptor.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION;
import static com.predic8.membrane.core.http.Response.internalServerError;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenRefresherTest {

    private AuthorizationService auth;
    private AccessTokenRefresher refresher;

    @BeforeEach
    void setUp() {
        auth = mock(AuthorizationService.class);
        refresher = new AccessTokenRefresher();
        refresher.init(auth, false);
    }

    /**
     * The authorization server could not be reached. The session's refresh token is untouched by that,
     * so logging the user out turns a blip at the identity provider into a forced re-login for every
     * user whose token happened to expire during it.
     */
    @Test
    void communicationErrorKeepsTheSessionAuthenticated() throws Exception {
        when(auth.refreshTokenRequest(any(), any(), anyString())).thenThrow(communicationError());
        Session session = expiredSession();

        OAuth2Exception e = assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(session, get("/foo").buildExchange()));

        assertEquals(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR, e.getError());
        assertTrue(session.isVerified());
    }

    /**
     * Anything else - a rejected refresh token above all - really does mean the session cannot be
     * refreshed, and restarting the flow is the way out.
     */
    @Test
    void otherFailuresStillClearTheAuthentication() throws Exception {
        when(auth.refreshTokenRequest(any(), any(), anyString())).thenThrow(new RuntimeException("invalid_grant"));
        Session session = expiredSession();

        refresher.refreshIfNeeded(session, get("/foo").buildExchange());

        assertFalse(session.isVerified());
    }

    @Test
    void nothingHappensWhileTheTokenIsStillValid() throws Exception {
        Session session = sessionWith("3600", LocalDateTime.now());

        refresher.refreshIfNeeded(session, get("/foo").buildExchange());

        assertTrue(session.isVerified());
    }

    private static OAuth2Exception communicationError() {
        return new OAuth2Exception(
                MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR,
                MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION,
                internalServerError().body(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION).build());
    }

    private static Session expiredSession() throws Exception {
        return sessionWith("60", LocalDateTime.now().minusHours(1));
    }

    private static Session sessionWith(String expiresInSeconds, LocalDateTime receivedAt) throws Exception {
        OAuth2AnswerParameters params = new OAuth2AnswerParameters();
        params.setAccessToken("expired-access-token");
        params.setRefreshToken("the-refresh-token");
        params.setExpiration(expiresInSeconds);
        params.setReceivedAt(receivedAt);

        Session session = new Session("username", new HashMap<>());
        session.setOAuth2Answer(params.serialize());
        session.authorize("alice");
        return session;
    }
}
