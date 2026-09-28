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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2Exception;
import com.predic8.membrane.core.interceptor.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2TokenResponseBody;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.time.LocalDateTime;
import java.util.HashMap;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.communicationError;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor.WANTED_SCOPE;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2SessionFixtures.REFRESH_TOKEN;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2SessionFixtures.USERNAME;
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
     * <p>
     * The two requests get their own Session object on purpose: SessionManager rebuilds the session
     * from the cookie every time, so a backoff that relied on the object staying the same would cover
     * nothing at all.
     */
    @Test
    void furtherRequestsFailFastWhileTheServerIsUnreachable() throws Exception {
        when(auth.refreshTokenRequest(any(), any(), anyString())).thenThrow(communicationError());

        assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(expiredSession(), get("/foo").buildExchange()));
        Session secondRequest = expiredSession();
        OAuth2Exception second = assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(secondRequest, get("/foo").buildExchange()));

        assertEquals(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR, second.getError());
        verify(auth, times(1)).refreshTokenRequest(any(), any(), anyString());
        assertTrue(secondRequest.isVerified());
    }

    /**
     * The backoff is per session, so one session running into the outage must not make another one
     * fail without ever having asked.
     */
    @Test
    void anotherSessionStillAsks() throws Exception {
        when(auth.refreshTokenRequest(any(), any(), anyString())).thenThrow(communicationError());

        assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(expiredSession("refresh-token-of-alice"), get("/foo").buildExchange()));
        assertThrows(OAuth2Exception.class, () -> refresher.refreshIfNeeded(expiredSession("refresh-token-of-bob"), get("/foo").buildExchange()));

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

    /**
     * A scoped request on a session that carries no answer under the default key. Deriving the backoff
     * key happens outside the try block, so it must not deserialize that missing default answer: doing
     * so throws past the handler and turns a failed refresh into an error escaping the interceptor.
     */
    @Test
    void scopedSessionWithoutADefaultAnswerDoesNotEscape() throws Exception {
        OAuth2AnswerParameters params = new OAuth2AnswerParameters();
        params.setAccessToken(null);
        params.setRefreshToken(REFRESH_TOKEN);
        params.setExpiration("60");
        params.setReceivedAt(LocalDateTime.now().minusHours(1));

        Session session = new Session("username", new HashMap<>());
        session.setOAuth2Answer("the-scope", params.serialize());
        session.authorize(USERNAME);

        Exchange exc = get("/foo").buildExchange();
        exc.setProperty(WANTED_SCOPE, "the-scope");

        assertDoesNotThrow(() -> refresher.refreshIfNeeded(session, exc));
        assertFalse(session.isVerified(), "the failed refresh should have cleared the authentication");
    }

    @Test
    void nothingHappensWhileTheTokenIsStillValid() throws Exception {
        Session session = validSession();

        refresher.refreshIfNeeded(session, get("/foo").buildExchange());

        assertTrue(session.isVerified());
        verifyNoInteractions(auth);
    }

    /**
     * Regression test for #3356: concurrent requests of one user each work on their own
     * {@link Session} instance - the session is read per request and cached on the Exchange - so the
     * Session object cannot identify the session. Keying the monitor by it gave every request a
     * private lock and left the exchange of one refresh token completely unsynchronized.
     */
    @Test
    void concurrentRefreshesOfOneSessionDoNotOverlap() throws Exception {
        int threads = 8;
        CountingAuthorizationService counting = new CountingAuthorizationService();
        AccessTokenRefresher concurrentRefresher = new AccessTokenRefresher();
        concurrentRefresher.init(counting, false);

        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        startTogether.await();
                        concurrentRefresher.refreshIfNeeded(expiredSession(), get("/foo").buildExchange());
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        allDone.countDown();
                    }
                });
            }
            startTogether.countDown();
            assertTrue(allDone.await(30, TimeUnit.SECONDS), "workers did not finish");
        } finally {
            pool.shutdownNow();
        }

        if (!failures.isEmpty())
            throw new AssertionError(failures.size() + " of " + threads + " workers failed", failures.getFirst());

        assertEquals(1, counting.maxConcurrent.get(),
                "the same refresh token was exchanged by several threads at once");
        assertEquals(threads, counting.calls.get(), "every worker should still have refreshed");
    }

    /**
     * Records how many threads are inside refreshTokenRequest at the same time. The pause is what
     * makes an overlap observable at all.
     */
    private static class CountingAuthorizationService extends AuthorizationService {

        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();

        @Override
        public OAuth2TokenResponseBody refreshTokenRequest(Session session, String wantedScope, String refreshToken) throws Exception {
            calls.incrementAndGet();
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(50);
                return new OAuth2TokenResponseBody(this, new ByteArrayInputStream(
                        ("""
                         {"access_token":"new-access-token",                         "refresh_token":"new-refresh-token",                         "token_type":"Bearer","expires_in":"3600"}""").getBytes(UTF_8)));
            } finally {
                concurrent.decrementAndGet();
            }
        }

        @Override public void init() {}
        @Override public String getIssuer() { return "http://example.com"; }
        @Override public String getJwksEndpoint() { return "http://example.com/jwks"; }
        @Override public String getEndSessionEndpoint() { return "http://example.com/logout"; }
        @Override public String getLoginURL(String callbackURL) { return "http://example.com/login"; }
        @Override public String getUserInfoEndpoint() { return "http://example.com/userinfo"; }
        @Override public String getSubject() { return "sub"; }
        @Override protected String getTokenEndpoint() { return "http://example.com/token"; }
        @Override public String getRevocationEndpoint() { return "http://example.com/revoke"; }
    }
}
