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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import org.jetbrains.annotations.*;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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

        mockAuthServer.getTransport().getInterceptors().add(0, new AbstractInterceptor() {
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

        List<Thread> threadList = getThreads(limit, goodTests);

        for (Thread thread : threadList)
            thread.join();

        LOG.debug("joined");

        LOG.debug("cookie count = " + countCookies());

        int j = limit + 1;
        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init" + j).buildExchange();
        LOG.debug("getting " + excCallResource.getDestinations().get(0));
        excCallResource = browser.apply(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init" + j, body2.get("path"));

        assertEquals(limit, goodTests.get());

        assertEquals(1, countCookies());
    }

    private @NotNull List<Thread> getThreads(int limit, AtomicInteger goodTests) {
        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int j = i;
            Thread thread = new Thread(() -> {
                try {

                    Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init" + j).buildExchange();
                    LOG.debug("getting " + excCallResource.getDestinations().get(0));
                    excCallResource = browser.apply(excCallResource);
                    Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
                    assertEquals("/init" + j, body2.get("path"));

                    goodTests.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            thread.setName("init getter " + i);
            thread.start();
            threadList.add(thread);
        }
        return threadList;
    }

    @Test
    public void testConsecutiveCalls() throws Exception {
        AtomicInteger authCounter = new AtomicInteger(0);

        mockAuthServer.getTransport().getInterceptors().add(0, new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (exc.getRequest().getUri().startsWith("/auth"))
                    authCounter.incrementAndGet();

                return Outcome.CONTINUE;
            }
        });

        for (int j = 0; j < 2; j++) {
            Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init" + j).buildExchange();
            LOG.debug("getting " + excCallResource.getDestinations().get(0));
            excCallResource = browser.apply(excCallResource);
            Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
            assertEquals("/init" + j, body2.get("path"));
        }

        // expect the auth server to be hit exactly once, second call should have had a cookie
        assertEquals(1, authCounter.get());

        assertEquals(1, countCookies());
    }

    private int countCookies() {
        JwtConsumer jwtc = new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setExpectedIssuer("http://localhost:31337/")
                .build();

        int count = 0;

        for (Map.Entry<String, Map<String, String>> c : browser.cookie.entrySet()) {
            for (Map.Entry<String, String> d : c.getValue().entrySet()) {
                LOG.debug(c.getKey() + " " + d.getKey() + " " + d.getValue());
                try {
                    JwtClaims jwtClaims = jwtc.processToClaims(d.getKey());
                    for (Map.Entry<String, Object> entry : jwtClaims.getClaimsMap().entrySet()) {
                        LOG.debug(" " + entry.getKey() + ": " + entry.getValue());
                    }
                    LOG.debug("mine");
                    count++;
                } catch (InvalidJwtException e) {
                    e.printStackTrace();
                }
            }
        }

        return count;
    }

}
