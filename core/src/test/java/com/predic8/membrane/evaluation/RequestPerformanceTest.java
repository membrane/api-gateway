package com.predic8.membrane.evaluation;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.net.http.HttpResponse.BodyHandlers.discarding;

public class RequestPerformanceTest {

    private static final int THREAD_COUNT = 16;
    private static final int REQUESTS_PER_THREAD = 10000;
    private static final String TARGET_URL = "http://localhost:2000";

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        final int threadCount = args.length >= 1 ? Integer.parseInt(args[0]) : THREAD_COUNT;
        final int requestsPerThread = args.length >= 2 ? Integer.parseInt(args[1]) : REQUESTS_PER_THREAD;
        final String targetUrl = args.length >= 3 ? args[2] : TARGET_URL;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<ThreadResult>> futures = new ArrayList<>();
        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> getThreadResult(requestsPerThread, targetUrl, client, threadId)));
        }
        executor.shutdown();
        printResults(futures, threadCount, requestsPerThread);
    }

    private static void printResults(List<Future<ThreadResult>> futures, int threadCount, int requestsPerThread) {
        double totalTime = 0;
        for (Future<ThreadResult> future : futures) {
            try {
                ThreadResult result = future.get();
                System.out.printf("Thread %2d | Total Duration: %7.3f sec | RPS: %8.2f | Min Req: %8.6f sec | Max Req: %8.6f sec%n",
                        result.threadId, result.durationSeconds, result.requestsPerSecond, result.minRequestDuration, result.maxRequestDuration);
                totalTime += result.durationSeconds;
            } catch (Exception e) {
                System.err.println("Error retrieving result: " + e.getMessage());
            }
        }
        System.out.printf("RPS = %.2f | Total Requests: %d%n", (threadCount * requestsPerThread / (totalTime / threadCount)), (threadCount * requestsPerThread));

    }

    private static @NotNull ThreadResult getThreadResult(int requestsPerThread, String targetUrl, HttpClient client, int threadId) {
        long threadStartTime = System.nanoTime();
        double minDuration = Double.MAX_VALUE;
        double maxDuration = 0;

        for (int j = 0; j < requestsPerThread; j++) {
            long reqStart = System.nanoTime();
            try {
                client.send(HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .GET()
                        .build(), discarding());
            } catch (Exception e) {
                System.err.println("Thread " + threadId + " Request " + j + " failed: " + e.getMessage());
            }
            long reqEnd = System.nanoTime();
            double reqDuration = (reqEnd - reqStart) / 1_000_000_000.0;
            minDuration = min(minDuration, reqDuration);
            maxDuration = max(maxDuration, reqDuration);
        }
        long threadEndTime = System.nanoTime();
        double totalDurationSeconds = (threadEndTime - threadStartTime) / 1_000_000_000.0;
        double requestsPerSecond = requestsPerThread / totalDurationSeconds;
        return new ThreadResult(threadId, totalDurationSeconds, requestsPerSecond, minDuration, maxDuration);
    }

    private static class ThreadResult {
        int threadId;
        double durationSeconds;
        double requestsPerSecond;
        double minRequestDuration;
        double maxRequestDuration;

        ThreadResult(int threadId, double durationSeconds, double requestsPerSecond, double minRequestDuration, double maxRequestDuration) {
            this.threadId = threadId;
            this.durationSeconds = durationSeconds;
            this.requestsPerSecond = requestsPerSecond;
            this.minRequestDuration = minRequestDuration;
            this.maxRequestDuration = maxRequestDuration;
        }
    }
}
