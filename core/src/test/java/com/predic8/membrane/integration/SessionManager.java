/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.integration;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;
import org.apache.http.Header;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManager {

    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(new Object[][] {
                inMemory(),
                jwt()
        });
    }

    private static Object[] jwt() {
        return new Object[]{
                JwtSessionManager.class.getSimpleName(),
                (Supplier) JwtSessionManager::new
        };
    }

    private static Object[] inMemory() {
        return new Object[]{
                InMemorySessionManager.class.getSimpleName(),
                (Supplier) InMemorySessionManager::new
        };
    }

    public static final String REMEMBER_HEADER = "X-Remember-This";
    public static final int GATEWAY_PORT = 3061;

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void remembersThings(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception{
        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        String rememberThisFromServer = "";
        try(CloseableHttpClient client = getHttpClient()){

            try(CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(),ctx)){
                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }

            try(CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT),ctx)){
                rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }
        }

        assertEquals(rememberThis,rememberThisFromServer);

        httpRouter.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void sessionExpires(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception{
        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier, Duration.ZERO)));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        String rememberThisFromServer;
        try(CloseableHttpClient client = getHttpClient()){

            try(CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(),ctx)){
                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }

            try(CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT),ctx)){
                rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }
        }

        assertNotEquals(rememberThis,rememberThisFromServer);
        assertEquals("null", rememberThisFromServer);

        httpRouter.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void changeValueInSession(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception{
        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        String rememberThisFromServer;
        try(CloseableHttpClient client = getHttpClient()){

            try(CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(),ctx)){
                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }

            try(CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, "rememberThis").build(),ctx)){
                if(nameDummyField.equals("jwt")){
                    List<Header> collect = Arrays.stream(resp.getHeaders("Set-Cookie")).collect(Collectors.toList());
                    assertEquals(2,collect.size());

                    assertTrue(collect.stream().filter(v -> v.getValue().toLowerCase().contains(com.predic8.membrane.core.interceptor.session.SessionManager.VALUE_TO_EXPIRE_SESSION_IN_BROWSER.toLowerCase())).count() == 1);
                    Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
                }
            }

            try(CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT),ctx)){
                rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
            }
        }

        assertEquals("rememberThis",rememberThisFromServer);

        httpRouter.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void sessionCookie(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception{
        AbstractInterceptorWithSession abstractInterceptorWithSession = testInterceptor(smSupplier);
        abstractInterceptorWithSession.getSessionManager().setSessionCookie(true);

        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, abstractInterceptorWithSession));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        try(CloseableHttpClient client = getHttpClient()){

            for(int i = 0; i <= 100; i++) {
                try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                    if(resp.getFirstHeader("Set-Cookie") != null) {
                        allSetCookieHeadersExceptFor1970Expire(resp).forEach(c -> {
                            assertFalse(c.getValue().toLowerCase().contains("Expire".toLowerCase()));
                            assertFalse(c.getValue().toLowerCase().contains("Max-Age".toLowerCase()));
                        });
                    }
                    Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
                }
            }

            for(int i = 0; i <= 100; i++) {
                try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, UUID.randomUUID().toString()).build(), ctx)) {
                    if(resp.getFirstHeader("Set-Cookie") != null) {
                        allSetCookieHeadersExceptFor1970Expire(resp).forEach(c -> {
                            assertFalse(c.getValue().toLowerCase().contains("Expire".toLowerCase()));
                            assertFalse(c.getValue().toLowerCase().contains("Max-Age".toLowerCase()));
                        });
                    }
                    Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
                }
            }
        }

        httpRouter.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void expiresPartIsRefreshedOnAccess(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception{
        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        try(CloseableHttpClient client = getHttpClient()){

            String firstExpires;
            String secondExpires;

            try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                List<Header> setCookieHeaders = allSetCookieHeadersExceptFor1970Expire(resp).collect(Collectors.toList());
                assertEquals(1, setCookieHeaders.size());

                Header setCookieHeader = setCookieHeaders.stream().findFirst().get();
                firstExpires = Arrays.stream(setCookieHeader.getValue().split(";")).filter(part -> part.toLowerCase().contains("Expires".toLowerCase())).findFirst().get();

                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }

            Thread.sleep(1000);

            try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                List<Header> setCookieHeaders = allSetCookieHeadersExceptFor1970Expire(resp).collect(Collectors.toList());
                assertEquals(1, setCookieHeaders.size());

                Header setCookieHeader = setCookieHeaders.stream().findFirst().get();
                secondExpires = Arrays.stream(setCookieHeader.getValue().split(";")).filter(part -> part.toLowerCase().contains("Expires".toLowerCase())).findFirst().get();

                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }
            System.out.println(firstExpires);
            System.out.println(secondExpires);
            assertNotEquals(firstExpires,secondExpires);

            // throws if dates are not parsable - no assert available
            Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(firstExpires.split("=")[1]));
            Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(secondExpires.split("=")[1]));
        }

        httpRouter.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void parallelRequests(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception {
        HttpRouter httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();
        ExecutorService executor = Executors.newCachedThreadPool();

        int limit = 10000;

        CountDownLatch startAllInParallel = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(limit);

        CloseableHttpClient client = getHttpClient();

        for(int i = 0; i < limit; i++) {
            executor.execute(() -> {
                try {
                    startAllInParallel.await();

                    try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, "rememberThis").build(), ctx)) {
                        long wrongCookies = Arrays.stream(resp.getAllHeaders())
                                .map(Object::toString)
                                .filter(h -> h.toLowerCase().contains("cookie"))
                                .flatMap(h -> Arrays.stream(h.split(";")))
                                .filter(e -> e.contains("=true"))
                                .filter(e -> e.contains(","))
                                .count();

                        assertEquals(0,wrongCookies);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    allDone.countDown();
                }
            });
        }
        startAllInParallel.countDown();
        allDone.await();

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        client.close();
        httpRouter.stop();
    }

    private Stream<Header> allSetCookieHeadersExceptFor1970Expire(CloseableHttpResponse resp) {
        return Arrays.stream(resp.getHeaders("Set-Cookie")).filter(c -> !c.getValue().contains(com.predic8.membrane.core.interceptor.session.SessionManager.VALUE_TO_EXPIRE_SESSION_IN_BROWSER));
    }

    private CloseableHttpClient getHttpClient() {
        return HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    }

    private HttpClientContext getHttpClientContext() {
        BasicCookieStore cookieStore = new BasicCookieStore();
        HttpClientContext ctx = HttpClientContext.create();
        ctx.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
        return ctx;
    }

    private AbstractInterceptorWithSession testInterceptor(
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier,
            Duration... ttl) {
        if(ttl == null || ttl.length == 0)
            ttl = new Duration[] {Duration.ofSeconds(300)};

        AbstractInterceptorWithSession result = new AbstractInterceptorWithSession() {
            @Override
            protected Outcome handleRequestInternal(Exchange exc) throws Exception {
                String rememberThis = exc.getRequest().getHeader().getFirstValue(REMEMBER_HEADER);
                if(rememberThis == null)
                    exc.setResponse(Response.ok().header(REMEMBER_HEADER,getSessionManager().getSession(exc).get(REMEMBER_HEADER)).build());
                else {
                    getSessionManager().getSession(exc).put(REMEMBER_HEADER,rememberThis);
                    exc.setResponse(Response.ok().build());
                }
                return Outcome.RETURN;
            }

            @Override
            protected Outcome handleResponseInternal(Exchange exc) throws Exception {
                return handleResponse(exc);
            }
        };

        result.setSessionManager(smSupplier.get());
        result.getSessionManager().setExpiresAfterSeconds(ttl[0].getSeconds());
        return result;
    }

}
