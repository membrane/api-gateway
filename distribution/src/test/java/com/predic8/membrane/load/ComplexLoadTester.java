/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.load;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.json.JsonProtectionInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.proxies.Target;
import com.predic8.membrane.core.router.DefaultRouter;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Response;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static org.asynchttpclient.Dsl.asyncHttpClient;

/**
 * LoadTest tool to measure the throughput of Membrane for several distinct request-processing
 * scenarios ("cases"). Each case builds its own router on port {@link #PORT}. Cases that only
 * measure the cost of an interceptor short-circuit with a {@link ReturnInterceptor} instead of
 * forwarding to a backend, so the measurement isolates the interceptors under test rather than an
 * additional network hop; cases that measure the forwarding path itself proxy to a simulated
 * backend on {@link #BACKEND_PORT}. Cases run one after another, so results are not affected by
 * mutual contention.
 * <p>
 * To add a case, add an entry to {@link #CASES} with a name, request method/path/body and a
 * method that builds and starts the router for it.
 * <p>
 * Results (TOTAL=1,000,000, CONCURRENCY=100 unless noted):
 * <ul>
 * <li>2026-09-10 on macOS, Apple M5 Pro MacBook Pro, 48 GB:
 * <ul>
 * <li>Short circuit (100 byte POST -&gt; 200): ~93,200 RPS</li>
 * <li>Fruitshop OpenAPI + JSON protection (POST /products): ~92,100 RPS</li>
 * <li>Forward to a simulated backend (GET): ~39,400 RPS; flat across CONCURRENCY 25-200
 * (~37k-39k RPS), degrading above that (e.g. ~22k RPS at 1,600) since the worker thread
 * blocks synchronously on the outbound call per request - CPU/thread-contention-bound,
 * not connection- or OS-limited (confirmed no material TCP connection churn on the
 * proxy-to-backend hop; connections are pooled and reused as designed).</li>
 * </ul>
 * </li>
 * </ul>
 */
public class ComplexLoadTester {

    /**
     * Total number of requests to be sent per case during the load testing process.
     */
    public static final int TOTAL = 1_000_000;

    /**
     * Specifies the maximum number of concurrent requests allowed during the load testing process.
     */
    public static final int CONCURRENCY = 100;

    /**
     * Port the router under test listens on. Reused across cases since they run sequentially.
     * <p>
     * Randomized per run rather than fixed: on macOS, loopback TIME_WAIT entries for a fixed
     * port can fail to drain between successive runs of this tool and silently drop SYNs on that
     * port (connects time out after 10s instead of failing fast), which reads as a throughput
     * collapse that has nothing to do with Membrane. Picking a fresh port each run makes it very
     * unlikely to land on one poisoned by a previous run.
     */
    public static final int PORT = 20_000 + ThreadLocalRandom.current().nextInt(10_000);

    /**
     * Port the simulated backend listens on, for cases that forward to one. Randomized for the
     * same reason as {@link #PORT}; kept in a disjoint range so the two can never collide.
     */
    public static final int BACKEND_PORT = 30_000 + ThreadLocalRandom.current().nextInt(10_000);

    private record TestCase(String name, String method, String path, String contentType, String body, RouterFactory routerFactory) {
    }

    @FunctionalInterface
    private interface RouterFactory {
        DefaultRouter start() throws Exception;
    }

    private static final List<TestCase> CASES = List.of(
            new TestCase(
                    "Short circuit: accept a 100 byte POST, return 200",
                    "POST",
                    "/",
                    "text/plain",
                    "x".repeat(100),
                    ComplexLoadTester::startShortCircuit),
            new TestCase(
                    "Fruitshop OpenAPI + JSON protection: validate a POST /products request",
                    "POST",
                    "/shop/v2/products",
                    "application/json",
                    "{\"name\":\"Figs\",\"price\":2.70}",
                    ComplexLoadTester::startFruitshopValidation),
            new TestCase(
                    "Forward: GET request proxied to a simulated backend",
                    "GET",
                    "/",
                    null,
                    null,
                    ComplexLoadTester::startForwardToBackend)
    );

    public static void main(String[] args) throws Exception {
        for (TestCase testCase : CASES) {
            System.out.println();
            System.out.println("=== " + testCase.name() + " ===");
            DefaultRouter router = testCase.routerFactory().start();
            try {
                new ComplexLoadTester().executeTest("http://localhost:" + PORT + testCase.path(),
                        testCase.method(), testCase.contentType(), testCase.body());
            } finally {
                router.stop();
            }
        }
    }

    /**
     * A single proxy that returns 200 immediately without ever calling a backend.
     */
    private static DefaultRouter startShortCircuit() throws Exception {
        var router = new DefaultRouter();
        router.setExchangeStore(new ForgetfulExchangeStore());

        var api = new APIProxy();
        api.setKey(new APIProxyKey(PORT));
        api.getFlow().add(new ReturnInterceptor());
        router.add(api);

        router.start();
        System.out.println("Membrane started.");
        return router;
    }

    /**
     * A single proxy that validates the request body against the Fruitshop OpenAPI document and
     * runs it through {@link JsonProtectionInterceptor} (default limits) before short-circuiting
     * with 201, so the measurement isolates the combined validation + protection cost.
     */
    private static DefaultRouter startFruitshopValidation() throws Exception {
        var router = new DefaultRouter();
        router.setExchangeStore(new ForgetfulExchangeStore());

        var spec = new OpenAPISpec();
        spec.location = shippedFruitshopSpecLocation();
        spec.validateRequests = YES;

        var api = new APIProxy();
        api.setKey(new APIProxyKey(PORT));
        api.setOpenapi(List.of(spec));

        api.getFlow().add(new JsonProtectionInterceptor());

        var returnInterceptor = new ReturnInterceptor();
        returnInterceptor.setStatus(201);
        api.getFlow().add(returnInterceptor);

        router.add(api);
        router.start();
        System.out.println("Membrane started.");
        return router;
    }

    /**
     * Locates the Fruitshop OpenAPI document that ships as part of the distribution's own
     * {@code conf} folder ({@code distribution/router/conf/openapi/}), independent of the current
     * working directory the tool happens to be launched from (IDE vs. {@code mvn exec:java}).
     */
    private static String shippedFruitshopSpecLocation() throws URISyntaxException {
        File testClasses = new File(ComplexLoadTester.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File distributionModuleDir = testClasses.getParentFile().getParentFile();
        return new File(distributionModuleDir, "router/conf/openapi/fruitshop-v2-2-0.oas.yml").getAbsolutePath();
    }

    /**
     * A proxy that forwards every request to a simulated backend, which short-circuits with 200,
     * so the measurement includes one real proxy-to-backend network hop.
     */
    private static DefaultRouter startForwardToBackend() throws Exception {
        var router = new DefaultRouter();
        router.setExchangeStore(new ForgetfulExchangeStore());

        var backend = new APIProxy();
        backend.setKey(new APIProxyKey(BACKEND_PORT));
        backend.getFlow().add(new ReturnInterceptor());
        router.add(backend);

        var api = new APIProxy();
        api.setKey(new APIProxyKey(PORT));
        api.setTarget(new Target("localhost", BACKEND_PORT));
        router.add(api);

        router.start();
        System.out.println("Membrane started.");
        return router;
    }

    private void executeTest(String url, String method, String contentType, String body) throws InterruptedException, IOException {
        var semaphore = new Semaphore(CONCURRENCY);

        try (var client = asyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(Duration.ofMillis(60000))
                .setRequestTimeout(Duration.ofMillis(60000))
                .setMaxConnections(CONCURRENCY)
                .setMaxConnectionsPerHost(CONCURRENCY).build()); ExecutorService submitters =
                     Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {

            var ok = new LongAdder();
            var err = new LongAdder();
            var latch = new CountDownLatch(TOTAL);

            final AtomicInteger minAvailablePermits = new AtomicInteger(CONCURRENCY);

            long start = System.nanoTime();

            for (int i = 0; i < TOTAL; i++) {
                semaphore.acquire();
                submitters.submit(() -> {
                    prepareCall(client, url, method, contentType, body, ok, err, latch, semaphore, minAvailablePermits);
                });

            }


            latch.await();
            long end = System.nanoTime();

            double seconds = (end - start) / 1_000_000_000.0;
            System.out.println("RPS: " + (TOTAL / seconds));
            System.out.println("OK=" + ok.sum() + " ERR=" + err.sum());
            System.out.println("Max number of concurrent clients = " + (CONCURRENCY - minAvailablePermits.get()));

            submitters.close();
        }
    }

    private static void prepareCall(AsyncHttpClient client, String url, String method, String contentType, String body, LongAdder ok, LongAdder err, CountDownLatch latch, Semaphore semaphore, AtomicInteger minAvailablePermits) {
        var request = client.prepare(method, url);
        if (body != null)
            request.setHeader("Content-Type", contentType).setBody(body);
        request.execute(new AsyncCompletionHandler<Void>() {
            @Override
            public Void onCompleted(Response r) {
                if (r.getStatusCode() < 400)
                    ok.increment();
                else
                    err.increment();
                latch.countDown();
                calculateMinAvailablePermits();
                semaphore.release();
                return null;
            }

            @Override
            public void onThrowable(Throwable t) {
                err.increment();
                latch.countDown();
                calculateMinAvailablePermits();
                semaphore.release();
            }

            private void calculateMinAvailablePermits() {
                minAvailablePermits.updateAndGet(current -> Math.min(current, semaphore.availablePermits()));
            }
        });
    }
}
