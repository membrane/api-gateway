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
package com.predic8.membrane.core.interceptor.oauth2.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JwtSMOAuth2R2Test extends OAuth2ResourceTest {

    @Override
    protected void configureSessionManager(OAuth2Resource2Interceptor oauth2) {
        // do nothing: JWT is default
    }

    @Test
    public void testStateMerge() throws Exception {
        int limit = 2;
        CountDownLatch cdl = new CountDownLatch(limit);
        AtomicInteger goodTests = new AtomicInteger();

        mockAuthServer.getTransport().getInterceptors().addFirst(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                cdl.countDown();
                try {
                    cdl.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(exc);
            }
        });

        IntStream.range(0, limit)
                .mapToObj(i -> getStartedThread(() -> getAndCountSuccesses(goodTests::incrementAndGet, "/init" + i)))
                .toList()
                .forEach(OAuth2ResourceTest::joinThread);

        getInitAndCheckPath("/init" + (limit + 1));
        assertEquals(limit, goodTests.get());
        assertEquals(1, countCookies());
    }

    private void getAndCountSuccesses(Runnable onSuccess, String path) {
        try {
            getInitAndCheckPath(path);
            onSuccess.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void getInitAndCheckPath(String path) throws URISyntaxException, IOException {
        var response = browser.apply(get(getClientAddress() + path)).getResponse();
        var body = om.readValue(response.getBodyAsStream(), new TypeReference<Map<String, String>>() {});
        assertEquals(path, body.get("path"));
    }

    @Test
    public void testConsecutiveCalls() throws Exception {
        AtomicInteger authCounter = new AtomicInteger(0);

        mockAuthServer.getTransport().getInterceptors().addFirst(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (exc.getRequest().getUri().startsWith("/auth"))
                    authCounter.incrementAndGet();

                return Outcome.CONTINUE;
            }
        });

        for (int j = 0; j < 2; j++) {
            Response response = browser.apply(get(getClientAddress() + "/init" + j)).getResponse();
            var body = om.readValue(response.getBodyAsStream(), Map.class);
            assertEquals("/init" + j, body.get("path"));
        }

        // expect the auth server to be hit exactly once, second call should have had a cookie
        assertEquals(1, authCounter.get());
        assertEquals(1, countCookies());
    }

    private int countCookies() {
        synchronized (browser.cookie) {
            return browser.cookie.values().stream()
                    .map(Map::keySet)
                    .flatMap(Collection::stream)
                    .map(jwt -> (hasValidClaims(jwt)) ? 1 : 0)
                    .reduce(0, Integer::sum);
        }
    }

    private boolean hasValidClaims(String jwt) {
        JwtConsumer consumer = new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setExpectedIssuer("http://localhost:31337/")
                .build();
        try {
            consumer.processToClaims(jwt);
            return true;
        } catch (InvalidJwtException e) {
            e.printStackTrace();
            return false;
        }

    }
}
