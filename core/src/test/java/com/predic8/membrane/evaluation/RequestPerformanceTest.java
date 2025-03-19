package com.predic8.membrane.evaluation;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
            int finalI = i;
            futures.add(executor.submit(() -> getThreadResult(requestsPerThread, targetUrl, client, finalI)));
        }
        executor.shutdown();
        printResults(futures, threadCount, requestsPerThread);
    }

    private static void printResults(List<Future<ThreadResult>> futures, int threadCount, int requestsPerThread) {
        double totalTime = futures.stream().mapToDouble(future -> {
            try {
                ThreadResult result = future.get();
                System.out.printf("Thread %d: Duration = %.3f sec, RPS = %.2f%n", result.threadId, result.durationSeconds, result.requestsPerMinute);
                return result.durationSeconds;
            } catch (Exception e) {
                System.err.println("Error retrieving result: " + e.getMessage());
                return 0.0;
            }
        }).sum();

        final double averageTime = totalTime / threadCount;
        System.out.println("Average per thread: Duration = " + averageTime + " sec, RPS = " + (threadCount*requestsPerThread / averageTime));
        System.out.println("Total Requests: " + threadCount * requestsPerThread);
    }

    private static @NotNull ThreadResult getThreadResult(int requestsPerThread, String targetUrl, HttpClient client, int threadId) {
        long startTime = System.nanoTime();
        for (int j = 0; j < requestsPerThread; j++) {
            try {
                client.send(HttpRequest.newBuilder().uri(URI.create(targetUrl)).GET().build(), discarding());
            } catch (Exception e) {
                System.err.println("Thread " + threadId + " Request " + j + " failed: " + e.getMessage());
            }
        }
        return new ThreadResult(
                threadId,
                (System.nanoTime() - startTime) / 1_000_000_000.0,
                (requestsPerThread / ((System.nanoTime() - startTime) / 1_000_000_000.0))
        );
    }

    private static class ThreadResult {
        int threadId;
        double durationSeconds;
        double requestsPerMinute;

        ThreadResult(int threadId, double durationSeconds, double requestsPerMinute) {
            this.threadId = threadId;
            this.durationSeconds = durationSeconds;
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}
