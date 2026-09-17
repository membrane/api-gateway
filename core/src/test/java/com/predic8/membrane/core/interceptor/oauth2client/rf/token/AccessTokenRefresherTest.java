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

import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2Exception;
import com.predic8.membrane.core.interceptor.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.communicationError;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2SessionFixtures.expiredSession;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2SessionFixtures.validSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
     * Every further request of the session would otherwise queue up on the refresh monitor and wait out
     * its own connect timeout, so a down authorization server would cost one timeout per request and
     * keep being hammered while it is already struggling.
     */
    @Test
    void furtherRequestsFailFastWhileTheServerIsUnreachable() throws Exception {
        when(auth.refreshTokenRequest(any(), any(), anyString())).thenThrow(communicationError());
        Session session = expiredSession();

        assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(session, get("/foo").buildExchange()));
        OAuth2Exception second = assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(session, get("/foo").buildExchange()));

        assertEquals(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR, second.getError());
        verify(auth, times(1)).refreshTokenRequest(any(), any(), anyString());
        assertTrue(session.isVerified());
    }

    /**
     * The backoff is per session, so one session running into the outage must not make another one
     * fail without ever having asked.
     */
    @Test
    void anotherSessionStillAsks() throws Exception {
        when(auth.refreshTokenRequest(any(), any(), anyString())).thenThrow(communicationError());

        assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(expiredSession(), get("/foo").buildExchange()));
        assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(expiredSession(), get("/foo").buildExchange()));

        verify(auth, times(2)).refreshTokenRequest(any(), any(), anyString());
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
        Session session = validSession();

        refresher.refreshIfNeeded(session, get("/foo").buildExchange());

        assertTrue(session.isVerified());
        verifyNoInteractions(auth);
    }
}
