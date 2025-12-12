package com.predic8.membrane.core;

import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import org.asynchttpclient.*;

import java.io.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.asynchttpclient.Dsl.*;

/**
 * LoadTest tool to measure the throughput of a Membrane and the maximum number of concurrent requests allowed.
 * <p>
 * Results:
 * - 2025-12-11 on MacOS MacBook Pro Apple M1 Max 2021
 * > 20.000 RPS
 * > Max number of concurrent clients = 10,000
 * Then the limits of the OS are limiting the number of concurrent connections.
 */
public class LoadTester {

    /**
     * Total number of requests to be sent during the load testing process.
     */
    public static final int TOTAL = 1_000_000;

    /**
     * Specifies the maximum number of concurrent requests allowed during the load testing process.
     */
    public static final int CONCURRENCY = 15000;

    Router r = new HttpRouter();

    public static void main(String[] args) throws Exception {
        var instance = new LoadTester();
        instance.startMembrane();
        instance.executeTest();
        instance.r.stop();
    }

    private void startMembrane() throws Exception {

        var backend = new APIProxy();
        backend.setKey(new APIProxyKey(2010));
        backend.getFlow().add(new ReturnInterceptor());
        r.add(backend);

        var api = new APIProxy();
        api.setKey(new APIProxyKey(2000));
        api.setTarget(new AbstractServiceProxy.Target("localhost", 2010));
        r.add(api);

        r.start();
        System.out.println("Membrane started.");
    }


    private void executeTest() throws InterruptedException, IOException {
        String url = "http://localhost:2000/";

        Semaphore semaphore = new Semaphore(CONCURRENCY);

        AsyncHttpClient client = asyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(Duration.ofMillis(60000))
                .setRequestTimeout(Duration.ofMillis(60000))
                .setMaxConnections(CONCURRENCY)
                .setMaxConnectionsPerHost(CONCURRENCY).build());

        ExecutorService submitters =
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

        LongAdder ok = new LongAdder();
        LongAdder err = new LongAdder();
        CountDownLatch latch = new CountDownLatch(TOTAL);

        final AtomicInteger minAvailablePermits = new AtomicInteger(CONCURRENCY);

        long start = System.nanoTime();

        for (int i = 0; i < TOTAL; i++) {
            semaphore.acquire();
            submitters.submit(() -> {
                client.preparePost(url).setBody("Dummy").execute(new AsyncCompletionHandler<Void>() {
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
            });

        }

        latch.await();
        long end = System.nanoTime();

        double seconds = (end - start) / 1_000_000_000.0;
        System.out.println("RPS: " + (TOTAL / seconds));
        System.out.println("OK=" + ok.sum() + " ERR=" + err.sum());
        System.out.println("Max number of concurrent clients = " + (CONCURRENCY - minAvailablePermits.get()));

        submitters.close();
        client.close();
    }
}
