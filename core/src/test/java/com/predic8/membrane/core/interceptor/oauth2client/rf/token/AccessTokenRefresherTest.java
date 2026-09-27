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
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2TokenResponseBody;
import com.predic8.membrane.core.interceptor.session.Session;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenRefresherTest {

    private static final String REFRESH_TOKEN = "the-refresh-token";

    /**
     * Concurrent requests of one user each work on their own {@link Session} instance - the session is
     * read per request and cached on the Exchange - so the Session object cannot identify the session.
     * Keying the monitor by it gave every request a private lock and left the exchange of one refresh
     * token completely unsynchronized.
     */
    @Test
    void concurrentRefreshesOfOneSessionDoNotOverlap() throws Exception {
        int threads = 8;
        CountingAuthorizationService auth = new CountingAuthorizationService();
        AccessTokenRefresher refresher = new AccessTokenRefresher();
        refresher.init(auth, false);

        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        startTogether.await();
                        refresher.refreshIfNeeded(sessionWithExpiredAccessToken(), new Exchange(null));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        allDone.countDown();
                    }
                });
            }
            startTogether.countDown();
            assertTrue(allDone.await(30, SECONDS), "workers did not finish");
        } finally {
            pool.shutdownNow();
        }

        if (!failures.isEmpty())
            throw new AssertionError(failures.size() + " of " + threads + " workers failed", failures.getFirst());

        assertEquals(1, auth.maxConcurrent.get(),
                "the same refresh token was exchanged by several threads at once");
        assertEquals(threads, auth.calls.get(), "every worker should still have refreshed");
    }

    /**
     * A session whose access token expired long ago, so that {@code refreshIfNeeded} actually refreshes.
     * A fresh instance per call, which is what a SessionManager hands each request.
     */
    private static Session sessionWithExpiredAccessToken() throws Exception {
        OAuth2AnswerParameters params = new OAuth2AnswerParameters();
        params.setAccessToken("expired-access-token");
        params.setRefreshToken(REFRESH_TOKEN);
        params.setExpiration("1");
        params.setReceivedAt(LocalDateTime.now().minusHours(1));

        Session session = new Session("username", new HashMap<>());
        session.setOAuth2Answer(params.serialize());
        return session;
    }

    /**
     * Records how many threads are inside {@code refreshTokenRequest} at the same time. The pause is what
     * makes an overlap observable at all - without it two threads would have to collide within a few
     * microseconds for the test to see it.
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
                         {"access_token":"new-access-token",\
                         "refresh_token":"new-refresh-token",\
                         "token_type":"Bearer","expires_in":"3600"}""").getBytes(UTF_8)));
            } finally {
                concurrent.decrementAndGet();
            }
        }

        @Override
        public void init() {
        }

        @Override
        public String getIssuer() {
            return "http://example.com";
        }

        @Override
        public String getJwksEndpoint() {
            return "http://example.com/jwks";
        }

        @Override
        public String getEndSessionEndpoint() {
            return "http://example.com/logout";
        }

        @Override
        public String getLoginURL(String callbackURL) {
            return "http://example.com/login";
        }

        @Override
        public String getUserInfoEndpoint() {
            return "http://example.com/userinfo";
        }

        @Override
        public String getSubject() {
            return "sub";
        }

        @Override
        protected String getTokenEndpoint() {
            return "http://example.com/token";
        }

        @Override
        public String getRevocationEndpoint() {
            return "http://example.com/revoke";
        }
    }
}
