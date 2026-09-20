package com.predic8.membrane.load;

import org.asynchttpclient.*;

import java.io.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

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
 */
public class LoadTesterClient {

    public static void main(String[] args) throws InterruptedException, IOException {
        var url = args.length > 0
                ? args[0]
                : System.getenv().getOrDefault("TARGET_URL", "http://localhost:2000/");

        int total = Integer.parseInt(System.getenv().getOrDefault("LOAD_TOTAL", "1000000"));
        int concurrency = Integer.parseInt(System.getenv().getOrDefault("LOAD_CONCURRENCY", "200"));
        int warmup = Integer.parseInt(System.getenv().getOrDefault("LOAD_WARMUP", "10000"));
        String method = System.getenv().getOrDefault("LOAD_METHOD", "POST");
        String body = System.getenv().getOrDefault("LOAD_BODY", "Dummy");
        String contentType = System.getenv().get("LOAD_CONTENT_TYPE");
        if (contentType != null && contentType.isEmpty())
            contentType = null;

        System.out.println("Target: " + url + "  METHOD=" + method + "  TOTAL=" + total + "  CONCURRENCY=" + concurrency + "  WARMUP=" + warmup);

        try (var client = asyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(Duration.ofMillis(60000))
                .setRequestTimeout(Duration.ofMillis(60000))
                .setMaxConnections(concurrency)
                .setMaxConnectionsPerHost(concurrency).build()); ExecutorService submitters =
                     Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {

            if (warmup > 0) {
                System.out.println("Warming up (" + warmup + " untimed requests)...");
                runPhase(client, submitters, url, method, body, contentType, warmup, concurrency, false);
                System.out.println("Warmup done.");
            }

            System.out.println("Starting measured run...");
            runPhase(client, submitters, url, method, body, contentType, total, concurrency, true);
        }
    }

    private static void runPhase(AsyncHttpClient client, ExecutorService submitters, String url, String method, String body,
                                  String contentType, int count, int concurrency, boolean timed) throws InterruptedException {
        var semaphore = new Semaphore(concurrency);
        var ok = new LongAdder();
        var err = new LongAdder();
        var latch = new CountDownLatch(count);
        final AtomicInteger minAvailablePermits = new AtomicInteger(concurrency);

        long startMillis = System.currentTimeMillis();
        long start = System.nanoTime();

        for (int i = 0; i < count; i++) {
            semaphore.acquire();
            submitters.submit(() -> {
                prepareCall(client, url, method, body, contentType, ok, err, latch, semaphore, minAvailablePermits);
            });
        }

        latch.await();
        long end = System.nanoTime();
        long endMillis = System.currentTimeMillis();

        if (timed) {
            System.out.println("MEASURED_WINDOW " + startMillis + " " + endMillis);
            double seconds = (end - start) / 1_000_000_000.0;
            System.out.println("RPS: " + (count / seconds));
            System.out.println("OK=" + ok.sum() + " ERR=" + err.sum());
            System.out.println("Max number of concurrent clients = " + (concurrency - minAvailablePermits.get()));
        }
    }

    private static void prepareCall(AsyncHttpClient client, String url, String method, String body, String contentType, LongAdder ok, LongAdder err, CountDownLatch latch, Semaphore semaphore, AtomicInteger minAvailablePermits) {
        var request = "GET".equalsIgnoreCase(method)
                ? client.prepareGet(url)
                : client.preparePost(url).setBody(body);
        if (contentType != null)
            request.setHeader("Content-Type", contentType);
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
