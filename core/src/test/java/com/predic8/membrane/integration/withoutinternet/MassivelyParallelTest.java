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

package com.predic8.membrane.integration.withoutinternet;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.lang.Thread.*;
import static java.util.concurrent.ConcurrentHashMap.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests parallel incoming requests (most probably via ~1.000 parallel TCP connections). Each request uses a unique
 * path.
 * <p>
 * The requests wait for 1000ms on the server (reducing the probability that connections are reused during the test) and
 * are responded to with 200 OK and their request.uri as body. Back on the client, the response body is asserted to be
 * the request path.
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
                    sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exc.setResponse(ok().body(exc.getRequest().getUri()).build());
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
    @Timeout(30) // seconds
    public void run() throws Exception {
        Set<String> paths = newKeySet();
        runInParallel((cdl) -> parallelTestWorker(cdl, paths), 1000);
        assertEquals(1000, paths.size());
    }

    private void runInParallel(Consumer<CountDownLatch> job, int threadCount) {
        try(ExecutorService es = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch cdl = new CountDownLatch(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    es.submit(() -> job.accept(cdl));
                }
                es.shutdown();
                if (!es.awaitTermination(30, TimeUnit.SECONDS)) {
                    es.shutdownNow();
                    fail("Tasks did not complete within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                es.shutdownNow();
                fail("Interrupted while waiting for tasks to complete");
            }
        }
    }

    private void parallelTestWorker(CountDownLatch cdl, Set<String> paths) {
        try {
            cdl.countDown();
            cdl.await();

            String uuid = UUID.randomUUID().toString();
            var exchange = client.call(get("http://localhost:3067/api/" + uuid).buildExchange());

            assertEquals(200, exchange.getResponse().getStatusCode());
            var body = exchange.getResponse().getBodyAsStringDecoded();
            assertEquals("/api/" + uuid, body);
            paths.add(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
