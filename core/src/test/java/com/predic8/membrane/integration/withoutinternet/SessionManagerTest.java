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

package com.predic8.membrane.integration.withoutinternet;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.integration.*;
import org.apache.http.Header;
import org.apache.http.client.config.*;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.*;
import org.apache.http.impl.client.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerTest {

    public static Collection<Object[]> data() {
        return Arrays.asList(inMemory(),
                jwt());
    }

    /**
     * Only the managers that keep the session in a store on the server. JwtSessionManager is left out
     * because it has no such store: it round-trips the whole session through the cookie, so parallel
     * requests come back as several valid cookies that SessionManager.mergeCookies joins - a different
     * mechanism with different (duplicating) semantics.
     */
    public static Collection<Object[]> storeBackedData() {
        return Arrays.asList(inMemory(),
                fakeSyncStore());
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

    private static Object[] fakeSyncStore() {
        return new Object[]{
                FakeSyncSessionStoreManager.class.getSimpleName(),
                (Supplier) FakeSyncSessionStoreManager::new
        };
    }

    @SuppressWarnings("UastIncorrectHttpHeaderInspection")
    public static final String REMEMBER_HEADER = "X-Remember-This";
    @SuppressWarnings("UastIncorrectHttpHeaderInspection")
    public static final String APPEND_HEADER = "X-Append-State";
    public static final int GATEWAY_PORT = 3061;

    /** The key StateManager keeps its CSRF tokens under - one of the keys declared additive. */
    private static final String STATE_KEY = SessionManager.SESSION_PARAMETER_STATE;

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void remembersThings(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception {
        var httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        String rememberThisFromServer = "";
        try (CloseableHttpClient client = getHttpClient()) {

            try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }

            try (CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT), ctx)) {
                rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }
        }

        assertEquals(rememberThis, rememberThisFromServer);

        httpRouter.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void sessionExpires(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception {
        var httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier, Duration.ZERO)));

        try {
            HttpClientContext ctx = getHttpClientContext();

            String rememberThis = UUID.randomUUID().toString();
            String rememberThisFromServer;
            try (CloseableHttpClient client = getHttpClient()) {

                try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                    Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
                }

                try (CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT), ctx)) {
                    rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
                    Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
                }
            }

            assertNotEquals(rememberThis, rememberThisFromServer);
            assertEquals("", rememberThisFromServer);
        } finally {
            httpRouter.stop();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void changeValueInSession(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception {
        var httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        String rememberThisFromServer;
        try (CloseableHttpClient client = getHttpClient()) {

            try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }

            try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, "rememberThis").build(), ctx)) {
                if (nameDummyField.equals("jwt")) {
                    List<Header> collect = Arrays.stream(resp.getHeaders("Set-Cookie")).toList();
                    assertEquals(2, collect.size());

                    assertEquals(1, collect.stream().filter(v -> v.getValue().toLowerCase().contains(SessionManager.VALUE_TO_EXPIRE_SESSION_IN_BROWSER.toLowerCase())).count());
                    Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
                }
            }

            try (CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT), ctx)) {
                rememberThisFromServer = resp.getFirstHeader(REMEMBER_HEADER).getValue();
            }
        }

        assertEquals("rememberThis", rememberThisFromServer);

        httpRouter.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void sessionCookie(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception {
        AbstractInterceptorWithSession abstractInterceptorWithSession = testInterceptor(smSupplier);
        abstractInterceptorWithSession.getSessionManager().setSessionCookie(true);

        var httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, abstractInterceptorWithSession));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        try (CloseableHttpClient client = getHttpClient()) {

            for (int i = 0; i <= 100; i++) {
                try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                    if (resp.getFirstHeader("Set-Cookie") != null) {
                        allSetCookieHeadersExceptFor1970Expire(resp).forEach(c -> {
                            assertFalse(c.getValue().toLowerCase().contains("Expire".toLowerCase()));
                            assertFalse(c.getValue().toLowerCase().contains("Max-Age".toLowerCase()));
                        });
                    }
                    Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
                }
            }

            for (int i = 0; i <= 100; i++) {
                try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, UUID.randomUUID().toString()).build(), ctx)) {
                    if (resp.getFirstHeader("Set-Cookie") != null) {
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
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception {
        var httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();

        String rememberThis = UUID.randomUUID().toString();
        try (CloseableHttpClient client = getHttpClient()) {

            String firstExpires;
            String secondExpires;

            try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                List<Header> setCookieHeaders = allSetCookieHeadersExceptFor1970Expire(resp).toList();
                assertEquals(1, setCookieHeaders.size());

                Header setCookieHeader = setCookieHeaders.stream().findFirst().get();
                firstExpires = Arrays.stream(setCookieHeader.getValue().split(";")).filter(part -> part.toLowerCase().contains("Expires".toLowerCase())).findFirst().get();

                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }

            Thread.sleep(1000);

            try (CloseableHttpResponse resp = client.execute(RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, rememberThis).build(), ctx)) {
                List<Header> setCookieHeaders = allSetCookieHeadersExceptFor1970Expire(resp).toList();
                assertEquals(1, setCookieHeaders.size());

                Header setCookieHeader = setCookieHeaders.stream().findFirst().get();
                secondExpires = Arrays.stream(setCookieHeader.getValue().split(";")).filter(part -> part.toLowerCase().contains("Expires".toLowerCase())).findFirst().get();

                Arrays.stream(resp.getAllHeaders()).forEach(h -> System.out.println(h.toString()));
            }
            System.out.println(firstExpires);
            System.out.println(secondExpires);
            assertNotEquals(firstExpires, secondExpires);

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
        var httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();
        ExecutorService executor = Executors.newCachedThreadPool();

        int limit = 10000;

        CountDownLatch startAllInParallel = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(limit);

        CloseableHttpClient client = getHttpClient();

        for (int i = 0; i < limit; i++) {
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

                        assertEquals(0, wrongCookies);
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

    /**
     * The same read-modify-write StateManager.saveToSession does: append one more token to a
     * SESSION_VALUE_SEPARATOR-joined list so that several authorization flows can be in flight at once.
     */
    private static void appendToState(Session session, String token) {
        String current = session.get(STATE_KEY);
        session.put(STATE_KEY, current == null ? token : current + SessionManager.SESSION_VALUE_SEPARATOR + token);
    }

    /**
     * Reproduces <a href="https://github.com/membrane/api-gateway/issues/3238">#3238</a>: concurrent
     * requests on one session each read their own copy of the session and write the whole thing back,
     * so every writer but the last loses its append. StateManager.saveToSession does exactly this
     * append to add a CSRF token for one more in-flight authorization flow; a token dropped here makes
     * the callback fail with "CSRF token mismatch."
     * <p>
     * hasExactlyOneMatchingToken requires the token to appear <i>exactly once</i>, so a duplicate is as
     * fatal as a loss - hence both assertions.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("storeBackedData")
    public void concurrentAppendsToOneSessionKeyAreNotLost(
            String nameDummyField,
            Supplier<com.predic8.membrane.core.interceptor.session.SessionManager> smSupplier) throws Exception {
        int limit = 200;
        var httpRouter = Util.basicRouter(Util.createServiceProxy(GATEWAY_PORT, testInterceptor(smSupplier)));

        HttpClientContext ctx = getHttpClientContext();
        ExecutorService executor = Executors.newFixedThreadPool(limit);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try (CloseableHttpClient client = getHttpClient(limit)) {
            // Establish the session first. Without a cookie every parallel request would start its own.
            try (CloseableHttpResponse resp = client.execute(
                    RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(REMEMBER_HEADER, "init").build(), ctx)) {
                assertEquals(200, resp.getStatusLine().getStatusCode());
            }

            List<String> tokens = IntStream.range(0, limit).mapToObj(i -> "token" + i).toList();
            CountDownLatch startAllInParallel = new CountDownLatch(1);
            CountDownLatch allDone = new CountDownLatch(limit);

            for (String token : tokens) {
                executor.execute(() -> {
                    try {
                        startAllInParallel.await();
                        try (CloseableHttpResponse resp = client.execute(
                                RequestBuilder.get("http://localhost:" + GATEWAY_PORT).addHeader(APPEND_HEADER, token).build(), ctx)) {
                            assertEquals(200, resp.getStatusLine().getStatusCode());
                        }
                    } catch (Throwable t) {
                        // Collected instead of thrown into the executor, where it would be swallowed.
                        failures.add(t);
                    } finally {
                        allDone.countDown();
                    }
                });
            }
            startAllInParallel.countDown();
            allDone.await();

            if (!failures.isEmpty())
                throw new AssertionError(failures.size() + " of " + limit + " workers failed", failures.getFirst());

            String state;
            try (CloseableHttpResponse resp = client.execute(new HttpGet("http://localhost:" + GATEWAY_PORT), ctx)) {
                state = resp.getFirstHeader(APPEND_HEADER).getValue();
            }

            List<String> stored = Arrays.asList(state.split(SessionManager.SESSION_VALUE_SEPARATOR));
            List<String> lost = tokens.stream().filter(t -> !stored.contains(t)).toList();
            assertEquals(List.of(), lost, lost.size() + " of " + limit + " tokens were lost");
            assertEquals(tokens.size(), stored.size(), "stored tokens: " + state);
        } finally {
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
            httpRouter.stop();
        }
    }

    private Stream<Header> allSetCookieHeadersExceptFor1970Expire(CloseableHttpResponse resp) {
        return Arrays.stream(resp.getHeaders("Set-Cookie")).filter(c -> !c.getValue().contains(com.predic8.membrane.core.interceptor.session.SessionManager.VALUE_TO_EXPIRE_SESSION_IN_BROWSER));
    }

    private CloseableHttpClient getHttpClient() {
        return HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    }

    private CloseableHttpClient getHttpClient(int maxConnections) {
        return HttpClients.custom()
                .setMaxConnTotal(maxConnections)
                .setMaxConnPerRoute(maxConnections)
                .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
                .build();
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
        if (ttl == null || ttl.length == 0)
            ttl = new Duration[]{Duration.ofSeconds(300)};

        AbstractInterceptorWithSession result = new AbstractInterceptorWithSession() {
            @Override
            protected Outcome handleRequestInternal(Exchange exc) {
                String appendThis = exc.getRequest().getHeader().getFirstValue(APPEND_HEADER);
                if (appendThis != null) {
                    appendToState(getSessionManager().getSession(exc), appendThis);
                    exc.setResponse(Response.ok().build());
                    return Outcome.RETURN;
                }

                String rememberThis = exc.getRequest().getHeader().getFirstValue(REMEMBER_HEADER);
                if (rememberThis == null)
                    exc.setResponse(Response.ok()
                            .header(REMEMBER_HEADER, getSessionManager().getSession(exc).get(REMEMBER_HEADER))
                            .header(APPEND_HEADER, Objects.toString(getSessionManager().getSession(exc).get(STATE_KEY), ""))
                            .build());
                else {
                    getSessionManager().getSession(exc).put(REMEMBER_HEADER, rememberThis);
                    exc.setResponse(Response.ok().build());
                }
                return Outcome.RETURN;
            }

            @Override
            protected Outcome handleResponseInternal(Exchange exc) {
                return handleResponse(exc);
            }
        };

        result.setSessionManager(smSupplier.get());
        result.getSessionManager().setExpiresAfterSeconds(ttl[0].getSeconds());
        return result;
    }

}
