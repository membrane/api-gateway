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

package com.predic8.membrane.load;

import org.asynchttpclient.*;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.asynchttpclient.Dsl.*;

/**
 * Standalone client role for the 3-VM Azure performance test (see ../README.md). Adapted from
 * the client half of LoadTester.executeTest()/prepareCall() so it can run on its own VM against
 * a gateway (or any HTTP target) reachable over the network instead of localhost.
 * <p>
 * Run: java -cp "client-libs/*:classes" com.predic8.membrane.load.LoadTesterClient [url]
 * Target URL defaults to env var TARGET_URL, then http://localhost:2000/.
 * Total requests: env var LOAD_TOTAL, default 1_000_000.
 * Concurrency: env var LOAD_CONCURRENCY, default 200.
 * Warmup requests (untimed, run before the measured phase): env var LOAD_WARMUP, default 10_000.
 * Authorization header (e.g. for the basic-auth scenario): env var LOAD_AUTHORIZATION, unset by default.
 * Trust any TLS certificate presented by the target, incl. self-signed (e.g. for the
 * rate-limit-basic-auth-tls scenario, whose gateway cert is self-signed): env var LOAD_INSECURE_TLS=true,
 * false by default. This client only ever talks to gateways this test itself stood up, so skipping
 * verification here doesn't weaken what's being measured on the gateway side.
 * <p>
 * The measured phase also reports latency percentiles (p50/p95/p99/max) over every completed
 * request, successful or not, timed from handing the request to the HTTP client until its response
 * or failure. This is a closed-loop client (a new request is only sent once one completes), so
 * latencies are not corrected for coordinated omission: a gateway stall delays the requests that
 * would have been sent during it instead of recording them as slow.
 * <p>
 * Fixed rate: env var LOAD_RATE (requests per second, unset by default) switches both phases to an
 * open-loop client. Request i is due at start + i / rate and is sent then, whether or not earlier
 * requests have completed, so the load stays constant while the gateway stalls. Latency is timed
 * from the due time, which counts a stall against every request that should have been sent during
 * it. LOAD_CONCURRENCY then caps the open connections. The run reports how far sending ever fell
 * behind the schedule; a large lag means the client, not the gateway, limited the rate.
 */
public class LoadTesterClient {

    public static void main(String[] args) throws InterruptedException, IOException {
        var url = args.length > 0
                ? args[0]
                : System.getenv().getOrDefault("TARGET_URL", "http://localhost:2000/");

        int total = Integer.parseInt(System.getenv().getOrDefault("LOAD_TOTAL", "1000000"));
        int concurrency = Integer.parseInt(System.getenv().getOrDefault("LOAD_CONCURRENCY", "200"));
        if (concurrency < 1)
            throw new IllegalArgumentException("LOAD_CONCURRENCY must be >= 1, got " + concurrency);
        int warmup = Integer.parseInt(System.getenv().getOrDefault("LOAD_WARMUP", "10000"));
        String method = System.getenv().getOrDefault("LOAD_METHOD", "POST");
        String body = System.getenv().getOrDefault("LOAD_BODY", "Dummy");
        String contentType = System.getenv().get("LOAD_CONTENT_TYPE");
        if (contentType != null && contentType.isEmpty())
            contentType = null;
        String authorization = System.getenv().get("LOAD_AUTHORIZATION");
        if (authorization != null && authorization.isEmpty())
            authorization = null;
        boolean insecureTls = Boolean.parseBoolean(System.getenv().getOrDefault("LOAD_INSECURE_TLS", "false"));
        String rateEnv = System.getenv("LOAD_RATE");
        double rate = rateEnv == null || rateEnv.isEmpty() ? 0 : Double.parseDouble(rateEnv);
        if (rate < 0)
            throw new IllegalArgumentException("LOAD_RATE must be > 0, got " + rateEnv);

        System.out.println("Target: " + url + "  METHOD=" + method + "  TOTAL=" + total + "  CONCURRENCY=" + concurrency + "  WARMUP=" + warmup + "  AUTH=" + (authorization != null) + "  INSECURE_TLS=" + insecureTls + "  RATE=" + (rate > 0 ? rate : "unlimited"));

        try (var client = asyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(Duration.ofMillis(60000))
                .setRequestTimeout(Duration.ofMillis(60000))
                .setMaxConnections(concurrency)
                .setMaxConnectionsPerHost(concurrency)
                .setUseInsecureTrustManager(insecureTls).build()); ExecutorService submitters =
                     Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {

            if (warmup > 0) {
                System.out.println("Warming up (" + warmup + " untimed requests)...");
                if (rate > 0)
                    runRatePhase(client, url, method, body, contentType, authorization, warmup, rate, false);
                else
                    runPhase(client, submitters, url, method, body, contentType, authorization, warmup, concurrency, false);
                System.out.println("Warmup done.");
            }

            System.out.println("Starting measured run...");
            if (rate > 0)
                runRatePhase(client, url, method, body, contentType, authorization, total, rate, true);
            else
                runPhase(client, submitters, url, method, body, contentType, authorization, total, concurrency, true);
        }
    }

    /**
     * Sends {@code count} requests at a fixed {@code rate} from one thread, without waiting for
     * responses (see the class Javadoc). Each response callback records its latency from the
     * request's due time.
     */
    private static void runRatePhase(AsyncHttpClient client, String url, String method, String body, String contentType,
                                     String authorization, int count, double rate, boolean timed) throws InterruptedException {
        var ok = new LongAdder();
        var err = new LongAdder();
        var done = new CountDownLatch(count);
        // One slot per request, written once by the callback of its response; awaiting the latch
        // makes every write visible to the percentile calculation.
        final long[] latenciesNanos = new long[count];
        double intervalNanos = 1_000_000_000.0 / rate;
        long maxLagNanos = 0;

        long startMillis = System.currentTimeMillis();
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            long due = start + (long) (i * intervalNanos);
            long wait;
            while ((wait = due - System.nanoTime()) > 0)
                LockSupport.parkNanos(wait);
            maxLagNanos = Math.max(maxLagNanos, -wait);

            int index = i;
            prepare(client, url, method, body, contentType, authorization).execute().toCompletableFuture()
                    .whenComplete((response, failure) -> {
                        latenciesNanos[index] = System.nanoTime() - due;
                        if (failure == null && response.getStatusCode() < 400)
                            ok.increment();
                        else
                            err.increment();
                        done.countDown();
                    });
        }
        long sendEnd = System.nanoTime();
        done.await();
        long end = System.nanoTime();
        long endMillis = System.currentTimeMillis();

        if (timed) {
            System.out.println("MEASURED_WINDOW " + startMillis + " " + endMillis);
            System.out.println("RPS: " + (count / ((end - start) / 1_000_000_000.0)));
            System.out.printf(Locale.ROOT, "SEND_RATE target=%.1f actual=%.1f maxLagMs=%.3f%n",
                    rate, count / ((sendEnd - start) / 1_000_000_000.0), maxLagNanos / 1_000_000.0);
            System.out.println("OK=" + ok.sum() + " ERR=" + err.sum());
            writeSlowRequests(latenciesNanos, startMillis, intervalNanos);
            printLatencyPercentiles(latenciesNanos);
        }
    }

    /**
     * Writes every request slower than LOAD_SLOW_MS (default 1) to the file LOAD_SLOW_LOG, if set,
     * one line per request with its due time (epoch ms) and latency (ms), so they can be matched against
     * the timestamps of GC pauses.
     */
    private static void writeSlowRequests(long[] latenciesNanos, long startMillis, double intervalNanos) {
        String file = System.getenv("LOAD_SLOW_LOG");
        if (file == null || file.isEmpty())
            return;
        long thresholdNanos = (long) (Double.parseDouble(System.getenv().getOrDefault("LOAD_SLOW_MS", "1")) * 1_000_000);
        int slow = 0;
        try (var out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            for (int i = 0; i < latenciesNanos.length; i++) {
                if (latenciesNanos[i] <= thresholdNanos)
                    continue;
                out.printf(Locale.ROOT, "%.3f %.3f%n", startMillis + i * intervalNanos / 1_000_000.0, latenciesNanos[i] / 1_000_000.0);
                slow++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("SLOW_REQUESTS " + slow + " " + file);
    }

    /**
     * Runs {@code count} requests on {@code concurrency} worker virtual threads. Each worker sends
     * one request, waits for its response and then sends the next, so exactly {@code concurrency}
     * requests are in flight until the last ones drain. A single thread that submitted every
     * request (acquire a permit, then send) could not keep up at several hundred thousand requests
     * per second: in some runs only a fraction of the permitted requests were in flight, and
     * throughput dropped by up to half while both client and gateway sat partly idle.
     */
    private static void runPhase(AsyncHttpClient client, ExecutorService submitters, String url, String method, String body,
                                  String contentType, String authorization, int count, int concurrency, boolean timed) throws InterruptedException {
        var ok = new LongAdder();
        var err = new LongAdder();
        var next = new AtomicInteger();
        // One slot per request, written once by the worker that sent it; joining the workers
        // below makes every write visible to the percentile calculation.
        final long[] latenciesNanos = new long[count];
        int workers = Math.min(concurrency, count);

        long startMillis = System.currentTimeMillis();
        long start = System.nanoTime();

        var futures = new ArrayList<Future<?>>(workers);
        for (int w = 0; w < workers; w++) {
            futures.add(submitters.submit(() -> {
                for (int i = next.getAndIncrement(); i < count; i = next.getAndIncrement())
                    sendAndWait(client, url, method, body, contentType, authorization, ok, err, latenciesNanos, i);
            }));
        }
        for (var future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new IllegalStateException("Load worker failed", e.getCause());
            }
        }
        long end = System.nanoTime();
        long endMillis = System.currentTimeMillis();

        if (timed) {
            System.out.println("MEASURED_WINDOW " + startMillis + " " + endMillis);
            double seconds = (end - start) / 1_000_000_000.0;
            System.out.println("RPS: " + (count / seconds));
            System.out.println("OK=" + ok.sum() + " ERR=" + err.sum());
            System.out.println("Concurrent workers = " + workers);
            printLatencyPercentiles(latenciesNanos);
        }
    }

    private static void printLatencyPercentiles(long[] latenciesNanos) {
        Arrays.sort(latenciesNanos);
        System.out.printf(Locale.ROOT, "LATENCY_MS p50=%.3f p95=%.3f p99=%.3f max=%.3f p99.9=%.3f p99.99=%.3f%n",
                percentileMillis(latenciesNanos, 50), percentileMillis(latenciesNanos, 95),
                percentileMillis(latenciesNanos, 99), latenciesNanos[latenciesNanos.length - 1] / 1_000_000.0,
                percentileMillis(latenciesNanos, 99.9), percentileMillis(latenciesNanos, 99.99));
    }

    /** Nearest-rank percentile of an ascending-sorted array. */
    private static double percentileMillis(long[] sortedNanos, double percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sortedNanos.length);
        return sortedNanos[Math.max(rank, 1) - 1] / 1_000_000.0;
    }

    private static BoundRequestBuilder prepare(AsyncHttpClient client, String url, String method, String body, String contentType, String authorization) {
        var request = "GET".equalsIgnoreCase(method)
                ? client.prepareGet(url)
                : client.preparePost(url).setBody(body);
        if (contentType != null)
            request.setHeader("Content-Type", contentType);
        if (authorization != null)
            request.setHeader("Authorization", authorization);
        return request;
    }

    private static void sendAndWait(AsyncHttpClient client, String url, String method, String body, String contentType, String authorization, LongAdder ok, LongAdder err, long[] latenciesNanos, int index) {
        var request = prepare(client, url, method, body, contentType, authorization);
        long sentNanos = System.nanoTime();
        try {
            var response = request.execute().get();
            latenciesNanos[index] = System.nanoTime() - sentNanos;
            if (response.getStatusCode() < 400)
                ok.increment();
            else
                err.increment();
        } catch (ExecutionException e) {
            latenciesNanos[index] = System.nanoTime() - sentNanos;
            err.increment();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a response", e);
        }
    }
}
