package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.MANUAL;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests 500 parallel incoming requests (most probably via ~500 parallel TCP connections). Each request uses a unique
 * path.
 *
 * The requests wait for 700ms on the server (reducing the probability that connections are reused during the test) and
 * are responded to with 200 OK and their request.uri as body. Back on the client, the response body is asserted to be
 * the request path.
 *
 * The test first starts with a ramp up of 20 parallel requests, waits for them to complete, and then proceeds to the
 * main task of 500.
 */
public class MassivelyParallelTest {

    static HttpClient client;
    static HttpRouter server;

    @BeforeAll
    public static void init() {
        client = new HttpClient();

        server = new HttpRouter();
        server.getTransport().setConcurrentConnectionLimitPerIp(1000);
        server.getTransport().setBacklog(1000);
        server.getTransport().setSocketTimeout(10000);
        server.setHotDeploy(false);
        server.getRuleManager().addProxy(createServiceProxy(), MANUAL);
        server.start();
    }

    private static ServiceProxy createServiceProxy() {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3067), null, 99999);

        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                exc.setResponse(Response.ok().body(exc.getRequest().getUri()).build());
                return RETURN;
            }
        });
        return sp;
    }


    @AfterAll
    public static void shutdown() {
        server.stop();
        client.close();
    }

    @Test
    public void run() throws Exception {
        System.out.println("test: begin.");
        Set<String> paths = new HashSet<>();
        // ramp up
        runInParallel((cdl) -> parallelTestWorker(cdl, paths), 20);
        // go
        runInParallel((cdl) -> parallelTestWorker(cdl, paths), 500);
        synchronized (paths) {
            assertEquals(520, paths.size());
        }
        System.out.println("test: end.");
    }

    private void runInParallel(Consumer<CountDownLatch> job, int threadCount) {
        List<Thread> threadList = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            threadList.add(new Thread(() -> job.accept(cdl)));
        }
        threadList.forEach(Thread::start);
        threadList.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void parallelTestWorker(CountDownLatch cdl, Set<String> paths) {
        try {
            cdl.countDown();
            cdl.await();

            String uuid = UUID.randomUUID().toString();
            var exchange = client.call(get("http://localhost:3067/api/" + uuid).buildExchange());

            var body = exchange.getResponse().getBodyAsStringDecoded();
            assertTrue(body.startsWith("/api/"));
            synchronized (paths) {
                paths.add(body);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
