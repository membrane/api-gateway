/* Copyright 2015 Fabian Kessler, Optimaize

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.interceptor;

import com.google.common.base.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.balancer.faultmonitoring.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.services.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.*;
import static java.util.concurrent.TimeUnit.*;
import static org.apache.commons.httpclient.HttpVersion.*;
import static org.apache.http.params.CoreProtocolPNames.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the LoadBalancingInterceptor using the SuccessStrategy.
 *
 * <p>Uses multi-threading with different amounts of endpoints, and turns off endpoints in between to
 * verify all is working fine.</p>
 *
 * @author Fabian Kessler / Optimaize
 */
class LoadBalancingInterceptorFaultMonitoringStrategyTest {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancingInterceptorFaultMonitoringStrategyTest.class.getName());

    LoadBalancingInterceptor balancingInterceptor;
    HttpRouter balancer;

    // The simulation nodes
    private final List<Router> nodes = new ArrayList<>();

    private void setUp(TestingContext ctx) throws Exception {
        nodes.clear();
        for (int i = 1; i <= ctx.numNodes; i++) {
            nodes.add(createRouterForNode(ctx, i));
        }

        balancer = createLoadBalancer();

        //add the destinations to the load balancer
        for (int i = 1; i <= ctx.numNodes; i++) {
            lookupBalancer(balancer, "Default").up("Default", "localhost", (2000 + i));
        }
    }

    private HttpRouter createLoadBalancer() throws Exception {
        HttpRouter r = new HttpRouter();
        ServiceProxy sp3 = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3054), "thomas-bayer.com", 80);
        balancingInterceptor = new LoadBalancingInterceptor();
        balancingInterceptor.setName("Default");
        sp3.getFlow().add(balancingInterceptor);
        r.getRuleManager().addProxyAndOpenPortIfNew(sp3);
        r.getTransport().getFirstInterceptorOfType(HTTPClientInterceptor.class).get().setHttpClientConfig(getHttpClientConfigurationWithRetries());
        r.init();
        return r;
    }

    private static @NotNull HttpClientConfiguration getHttpClientConfigurationWithRetries() {
        HttpClientConfiguration config = new HttpClientConfiguration();
        RetryHandler rh = config.getRetryHandler();
        rh.setRetries(5); // Cause we simulate nodes that are down
        rh.setDelay(1); // Fast for tests
        rh.setBackoffMultiplier(2);
        rh.setFailOverOn5XX(true);
        return config;
    }

    private Router createRouterForNode(TestingContext ctx, int i) throws Exception {
        HttpRouter r = new HttpRouter();
        r.getRuleManager().addProxyAndOpenPortIfNew(createServiceProxy(ctx, i));
        r.init();
        return r;
    }

    private @NotNull ServiceProxy createServiceProxy(TestingContext ctx, int i) {
        ServiceProxy serviceProxy = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", (2000 + i)), "thomas-bayer.com", 80);
        serviceProxy.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleResponse(Exchange exc) {
                exc.getResponse().getHeader().setConnection("close");
                return CONTINUE;
            }
        });
        serviceProxy.getFlow().add(new RandomlyFailingDummyWebServiceInterceptor(ctx.successChance));
        return serviceProxy;
    }

    @AfterEach
    void tearDown() {
        for (Router httpRouter : nodes) {
            try {
                httpRouter.shutdown();
            } catch (Exception e) {
                log.warn("Node shutdown failed.", e);
            }
        }
        try {
            balancer.shutdown();
        } catch (Exception e) {
            log.warn("Balancer shutdown failed.", e);
        }
    }

    /**
     * Because we set the success chance to 0, none will pass.
     */
    @Test
    void test_2destinations_6threads_100calls_allFail() throws Exception {
        TestingContext ctx = new TestingContext.Builder()
                .numNodes(2)
                .numThreads(6)
                .numRequests(100)
                .successChance(0d)
                .build();

        run(ctx);

        assertEquals(100, ctx.exceptionCounter.get());
        assertEquals(0, ctx.successCounter.get());
    }

    /**
     * Because we set the success chance to 1, all will pass.
     */
    @Test
    public void test_2destinations_6threads_100calls_allSucceed() throws Exception {
        TestingContext ctx = new TestingContext.Builder()
                .numNodes(2)
                .numThreads(6)
                .numRequests(100)
                .successChance(1d)
                .build();

        run(ctx);
        assertEquals(100, ctx.successCounter.get());
    }

    /**
     * The success rate at 0.5 is low, so every second request needs at least one retry.
     * Because of this, in the end not all will succeed.
     */
    @Test
    void test_2destinations_6threads_100calls_someFail() throws Exception {
        TestingContext ctx = new TestingContext.Builder()
                .numNodes(2)
                .numThreads(6)
                .numRequests(1000)
                .successChance(0.5d)
                .build();

        run(ctx);

        assertTrue(ctx.successCounter.get() >= 900, "ctx.successCounter.get() is %d, less than 900".formatted(ctx.successCounter.get()));
        assertTrue(ctx.exceptionCounter.get() < 100, "ctx.exceptionCounter.get() is %d, greater than 100".formatted(ctx.successCounter.get()));
        assertTrue(ctx.successCounter.get() < 1000, "ctx.successCounter.get() is %d, greater than 1000".formatted(ctx.successCounter.get()));
    }

    /**
     * After 20 requests we terminate one of the 5 destinations.
     * Because enough remain active, all requests will pass.
     * <p/>
     * Also, only after the termination a few requests may take long (around 1000ms) because of the retry on
     * the good destination. After that, the dispatcher must have realized to send all directly to the good server.
     * A few can go the wrong way because of multi-threading, they are on the way already.
     */
    @Test
    public void test_5destinations_6threads_100calls_1shutdown() throws Exception {
        TestingContext ctx = new TestingContext.Builder()
                .numNodes(5)
                .numThreads(6)
                .numRequests(100)
                .successChance(1d)
                .preSubmitCallback(integer -> {
                    if (integer == 20) {
                        nodes.getFirst().shutdown();
                    }
                    return null;
                })
                .build();

        run(ctx);

        assertTrue(ctx.successCounter.get() > 95,"ctx.successCounter.get() > 95 was %s".formatted(ctx.successCounter.get()));
        for (int i = 0; i < 100; i++) {
            if (i < 10 || i >= 40) {
                assertTrue(ctx.runtimes[i] < 500, "For " + i + " value was: " + ctx.runtimes[i]);
            }
        }
    }

    /**
     * After batches of 10 requests we terminate 4 of the 5 destinations.
     * Because not enough remain active, all destinations remain in the pool and are used by success rate chance.
     * But because 1 remains functional, in the end all requests succeed.
     */
    @Test
    void test_5destinations_6threads_100calls_4shutdown() throws Exception {
        TestingContext ctx = new TestingContext.Builder()
                .numNodes(5)
                .numThreads(6)
                .numRequests(100)
                .successChance(1d)
                .preSubmitCallback(i -> {
                    if (i == 10) {
                        nodes.getFirst().shutdown();
                    } else if (i == 20) {
                        nodes.get(1).shutdown();
                    } else if (i == 30) {
                        nodes.get(2).shutdown();
                    } else if (i == 40) {
                        nodes.get(3).shutdown();
                    }
                    return null;
                })
                .build();

        run(ctx);

        assertTrue(ctx.successCounter.get() >= 90, "ctx.successCounter.get() is %d, less than 90".formatted(ctx.successCounter.get()));
        assertTrue(ctx.exceptionCounter.get() < 10, "ctx.exceptionCounter.get() is %d, greater than 10".formatted(ctx.successCounter.get()));
    }

    /**
     * Contains the variables used in one test run.
     */
    private static class TestingContext {

        static class Builder {
            private int numNodes = 1;
            private DispatchingStrategy dispatchingStrategy = new FaultMonitoringStrategy();
            private int numThreads = 6;
            private int numRequests = 100;
            private double successChance = 1d;
            private Function<Integer, Void> preSubmitCallback = null;

            /**
             * How many nodes the load balancer should work with.
             */
            public Builder numNodes(int numNodes) {
                this.numNodes = numNodes;
                return this;
            }

            /**
             * Defaults to the {@link FaultMonitoringStrategy}.
             */
            public Builder dispatchingStrategy(DispatchingStrategy dispatchingStrategy) {
                this.dispatchingStrategy = dispatchingStrategy;
                return this;
            }

            /**
             * The service requests to the nodes are made using a ThreadPoolExecutor, using this many threads.
             *
             * @param numThreads default is 6
             */
            public Builder numThreads(int numThreads) {
                this.numThreads = numThreads;
                return this;
            }

            /**
             * How many service requests to send in total (not per thread).
             *
             * @param numRequests default is 100
             */
            public Builder numRequests(int numRequests) {
                this.numRequests = numRequests;
                return this;
            }

            /**
             * Each service request can be given a random chance to fail with a 5xx code.
             * See {@link RandomlyFailingDummyWebServiceInterceptor}.
             *
             * @param successChance 1.0 for always succeeding, 0.0 for never succeeding, and anything in between for a weighted likeliness.
             */
            public Builder successChance(double successChance) {
                this.successChance = successChance;
                return this;
            }

            /**
             * Before submitting a service request to the ThreadPoolExecutor one can perform some task, like
             * shutting down a node.
             * The given Integer is the service request counter starting at 0, therefore 99 means the 100th call.
             */
            public Builder preSubmitCallback(Function<Integer, Void> preSubmitCallback) {
                this.preSubmitCallback = preSubmitCallback;
                return this;
            }

            public TestingContext build() {
                return new TestingContext(numNodes, dispatchingStrategy, numRequests, numThreads, successChance, preSubmitCallback);
            }
        }

        private final int numNodes;
        private final DispatchingStrategy dispatchingStrategy;
        private final int numRequests;
        private final double successChance;
        private final Function<Integer, Void> preSubmitCallback;

        private final AtomicInteger runCounter = new AtomicInteger();
        private final AtomicInteger successCounter = new AtomicInteger();
        private final AtomicInteger exceptionCounter = new AtomicInteger();

        private final ThreadPoolExecutor tpe;

        /**
         * Collects the runtimes for each service call, in milliseconds.
         */
        final long[] runtimes;

        public TestingContext(int numNodes,
                              DispatchingStrategy dispatchingStrategy,
                              int numRequests,
                              int numThreads,
                              double successChance,
                              Function<Integer, Void> preSubmitCallback) {
            this.numNodes = numNodes;
            this.dispatchingStrategy = dispatchingStrategy;
            this.numRequests = numRequests;
            this.successChance = successChance;
            this.preSubmitCallback = preSubmitCallback;

            dispatchingStrategy.init(null);
            tpe = createThreadPoolExecutor(numThreads);
            runtimes = new long[numRequests];
        }


        public long getSlowestRuntime() {
            long maxTaskRuntime = 0L;
            for (long runtime : runtimes) {
                if (runtime > maxTaskRuntime) maxTaskRuntime = runtime;
            }
            return maxTaskRuntime;
        }

        public void shutdown() throws InterruptedException {
            tpe.shutdown();
            tpe.awaitTermination(10, SECONDS);
        }

    }


    private void run(TestingContext ctx) throws Exception {
        setUp(ctx);
        balancingInterceptor.setDispatchingStrategy(ctx.dispatchingStrategy);

        Stopwatch overallTime = Stopwatch.createStarted();

        submitTasks(ctx);
        ctx.shutdown();

        long totalTimeSpent = overallTime.elapsed(MILLISECONDS);

        log.info("Total time spent: {} ms, longest run was {} ms", totalTimeSpent, ctx.getSlowestRuntime());
        standardExpectations(ctx);
    }

    private void submitTasks(final TestingContext ctx) {
        for (int i = 0; i < ctx.numRequests; i++) {
            if (ctx.preSubmitCallback != null) {
                ctx.preSubmitCallback.apply(i);
            }
            final int runNumber = i;
            ctx.tpe.submit(() -> {
                Stopwatch taskTime = Stopwatch.createStarted();
                try {
                    final HttpClient client = getHttpClient();
                    var method = getPutMethod();
                    try {
                        int statusCode = client.executeMethod(method);
                        if (statusCode == 200) {
                            ctx.successCounter.incrementAndGet();
                        } else {
                            log.warn("Non-200 status code: {}", statusCode);
                            ctx.exceptionCounter.incrementAndGet();
                        }
                    } finally {
                        method.releaseConnection();
                    }

                } catch (Exception e) {
                    ctx.exceptionCounter.incrementAndGet();
                    log.error("Error", e);
                }
                ctx.runCounter.incrementAndGet();
                ctx.runtimes[runNumber] = taskTime.elapsed(MILLISECONDS);
            });
        }
    }

    private static @NotNull HttpClient getHttpClient() {
        final HttpClient client = new HttpClient();
        client.getParams().setParameter(PROTOCOL_VERSION, HTTP_1_1);
        return client;
    }


    private void standardExpectations(TestingContext ctx) {
        assertEquals(ctx.numRequests, ctx.runCounter.get());
        assertEquals(ctx.numRequests, ctx.exceptionCounter.get() + ctx.successCounter.get(), "Total = success + exception counts");
    }

    /**
     * @param numThreads 1-n
     */
    private static ThreadPoolExecutor createThreadPoolExecutor(int numThreads) {
        return new ThreadPoolExecutor(
                numThreads, numThreads,
                1, SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private PutMethod getPutMethod() {
        PutMethod put = new PutMethod(
                "http://localhost:3054/axis2/services/BLZService");
        put.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream("/getBank.xml")));
        put.setRequestHeader(CONTENT_TYPE, TEXT_XML_UTF8);
        put.setRequestHeader(SOAP_ACTION, "");
        return put;
    }
}