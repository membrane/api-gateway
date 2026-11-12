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

package com.predic8.membrane.core.schedule;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.proxies.InternalProxy;
import com.predic8.membrane.core.proxies.Target;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.router.hotdeploy.YamlRouterReloader;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import static com.predic8.membrane.core.exchange.ExchangeState.COMPLETED;
import static com.predic8.membrane.core.exchange.ExchangeState.FAILED;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static com.predic8.membrane.core.util.ExceptionUtil.getRootCause;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleTest {

    @TempDir
    Path dir;

    private TestRouter backend;
    private DefaultRouter gateway;

    /**
     * Calls the backend received and finished.
     */
    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private volatile String method;
    private volatile String path;
    private volatile long delayMillis;

    @BeforeEach
    void startBackend() throws Exception {
        backend = new TestRouter();
        backend.add(backendApi("/", 200));
        backend.add(backendApi("/fail", 500));
        backend.start();
    }

    @AfterEach
    void stopRouters() {
        if (gateway != null)
            gateway.stop();
        backend.stop();
    }

    private APIProxy backendApi(final String apiPath, final int status) {
        final var api = new APIProxy();
        api.setKey(new APIProxyKey(3000));
        api.getKey().setPath(apiPath);
        api.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(final Exchange exc) {
                started.incrementAndGet();
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    method = exc.getRequest().getMethod();
                    path = exc.getRequest().getUri();
                    Thread.sleep(delayMillis);
                    calls.incrementAndGet();
                    return CONTINUE;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    inFlight.decrementAndGet();
                }
            }
        });
        final var ret = new ReturnInterceptor();
        ret.setStatus(status);
        api.getFlow().add(ret);
        return api;
    }

    private static String config(final String schedule, final String targetUrl) {
        return """
                configuration:
                  hotDeploy: false
                ---
                internal:
                  name: job
                  schedule:
                    %s
                  target:
                    url: %s
                """.formatted(schedule, targetUrl);
    }

    private static String config(final String targetUrl) {
        return config("interval: PT0.1S", targetUrl);
    }

    private Path writeConfig(final String config) throws Exception {
        final var file = dir.resolve("apis.yaml");
        Files.writeString(file, config);
        return file;
    }

    private YamlRouterReloader startGateway(final String config) throws Exception {
        final var file = writeConfig(config);
        final var router = new DefaultRouter();
        final var reloader = new YamlRouterReloader(router, loadIntoRouter(router, file.toString()));
        gateway = router;
        gateway.setConfigurationReloader(reloader);
        gateway.start();
        return reloader;
    }

    private static void await(final IntSupplier count, final int atLeast) throws InterruptedException {
        final var deadline = System.currentTimeMillis() + 5_000;
        while (count.getAsInt() < atLeast) {
            if (System.currentTimeMillis() > deadline)
                fail("Expected at least %d calls, got %d.".formatted(atLeast, count.getAsInt()));
            Thread.sleep(20);
        }
    }

    private void assertNoMoreCalls() throws InterruptedException {
        // A call that was already running may still finish.
        Thread.sleep(300);
        final var before = started.get();
        Thread.sleep(500);
        assertEquals(before, started.get());
    }

    @Test
    void callsApiPeriodically() throws Exception {
        startGateway(config("http://localhost:3000"));
        await(calls::get, 3);
        assertEquals("GET", method);
        assertEquals("/", path);
    }

    @Test
    void failedCallDoesNotStopLaterCalls() throws Exception {
        startGateway(config("http://localhost:3000/fail"));
        await(calls::get, 3);
    }

    @Test
    void callsDoNotOverlap() throws Exception {
        delayMillis = 300;
        startGateway(config("http://localhost:3000"));
        await(calls::get, 2);
        assertEquals(1, maxInFlight.get());
    }

    @Test
    void loadingWithoutInitDoesNotCall() throws Exception {
        // Reload validation loads the configuration into a router that is never initialized.
        final var router = new DefaultRouter();
        try {
            loadIntoRouter(router, writeConfig(config("http://localhost:3000")).toString());
            assertNoMoreCalls();
            assertEquals(0, started.get());
        } finally {
            router.shutdownRuntimeComponents();
        }
    }

    @Test
    void stopsWithRouter() throws Exception {
        startGateway(config("http://localhost:3000"));
        await(calls::get, 1);
        gateway.stop();
        gateway = null;
        assertNoMoreCalls();
    }

    @Test
    void reloadWithoutScheduleStopsCalls() throws Exception {
        final var reloader = startGateway(config("http://localhost:3000"));
        await(calls::get, 1);
        writeConfig("""
                configuration:
                  hotDeploy: false
                ---
                internal:
                  name: job
                  target:
                    url: http://localhost:3000
                """);
        assertTrue(reloader.reload());
        assertNoMoreCalls();
    }

    @Test
    void intervalMustBePositive() {
        assertThrows(ConfigurationException.class, () -> new Schedule().setInterval("PT0S"));
        assertThrows(ConfigurationException.class, () -> new Schedule().setInterval("PT-1S"));
    }

    @Test
    void invalidDurationFailsWhileLoading() throws Exception {
        // Reload validation only loads the configuration, so a check during start() would come after the running
        // configuration was stopped.
        final var file = writeConfig(config("interval: PT0S", "http://localhost:3000")).toString();
        final var router = new DefaultRouter();
        try {
            final var e = assertThrows(Exception.class, () -> loadIntoRouter(router, file));
            final var root = assertInstanceOf(ConfigurationException.class, getRootCause(e));
            assertTrue(root.getMessage().contains("'interval' must be at least one millisecond"), root::getMessage);
        } finally {
            router.shutdownRuntimeComponents();
        }
    }

    @Test
    void failedResponseBodyReadFailsCall() throws Exception {
        try (final var truncating = new ServerSocket(0)) {
            Thread.ofVirtual().start(() -> answerWithTruncatedBody(truncating));

            final var router = new TestRouter();
            final var api = scheduledApi("PT0.1S");
            final var exchange = new AtomicReference<Exchange>();
            api.getFlow().add(new AbstractInterceptor() {
                @Override
                public Outcome handleRequest(final Exchange exc) {
                    exchange.compareAndSet(null, exc);
                    return CONTINUE;
                }
            });
            api.setTarget(new Target("localhost", truncating.getLocalPort()));
            router.add(api);
            try {
                router.start();
                await(() -> isFinished(exchange.get()) ? 1 : 0, 1);
                assertEquals(FAILED, exchange.get().getStatus());
                assertEquals("reading the response body failed", exchange.get().getErrorMessage());
            } finally {
                api.destroy();
                router.stop();
            }
        }
    }

    private static boolean isFinished(final Exchange exc) {
        return exc != null && (exc.getStatus() == COMPLETED || exc.getStatus() == FAILED);
    }

    private static InternalProxy scheduledApi(final String interval) {
        final var schedule = new Schedule();
        schedule.setInterval(interval);
        final var api = new InternalProxy();
        api.setName("job");
        api.setSchedule(schedule);
        return api;
    }

    /**
     * Announces a body of 100 bytes, sends 10 and closes the connection.
     */
    private static void answerWithTruncatedBody(final ServerSocket server) {
        try (final var socket = server.accept()) {
            final var in = socket.getInputStream();
            // Read the request header up to the empty line.
            var matched = 0;
            while (matched < 4) {
                final var b = in.read();
                if (b < 0)
                    return;
                matched = b == "\r\n\r\n".charAt(matched) ? matched + 1 : (b == '\r' ? 1 : 0);
            }
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n0123456789".getBytes(US_ASCII));
            socket.getOutputStream().flush();
        } catch (IOException ignored) {
            // The test fails on its own when the call does not get this answer.
        }
    }

    @Test
    void intervalMustBeIsoDuration() {
        assertThrows(ConfigurationException.class, () -> new Schedule().setInterval("10m"));
    }
}
