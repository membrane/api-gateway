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

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.balancer.BalancerUtil;
import com.predic8.membrane.core.interceptor.balancer.DispatchingStrategy;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.faultmonitoring.FaultMonitoringStrategy;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.services.RandomlyFailingDummyWebServiceInterceptor;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the LoadBalancingInterceptor using the SuccessStrategy.
 *
 * <p>Uses multi-threading with different amounts of endpoints, and turns off endpoints in between to
 * verify all is working fine.</p>
 *
 * @author Fabian Kessler / Optimaize
 */
public class LoadBalancingInterceptor_FaultMonitoringStrategyTest {

    protected LoadBalancingInterceptor balancingInterceptor;
    protected HttpRouter balancer;
    private List<HttpRouter> httpRouters = new ArrayList<HttpRouter>();
    private List<RandomlyFailingDummyWebServiceInterceptor> dummyInterceptors = new ArrayList<RandomlyFailingDummyWebServiceInterceptor>();

    private void setUp(TestingContext ctx) throws Exception {
        for (int i = 1; i <= ctx.numNodes; i++) {
            HttpRouter httpRouter = new HttpRouter();
            httpRouters.add(httpRouter);

            RandomlyFailingDummyWebServiceInterceptor dummyInterceptor = new RandomlyFailingDummyWebServiceInterceptor(ctx.successChance);
            dummyInterceptors.add(dummyInterceptor);

            ServiceProxy serviceProxy = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", (2000 + i)), "thomas-bayer.com", 80);
            serviceProxy.getInterceptors().add(new AbstractInterceptor() {
                @Override
                public Outcome handleResponse(Exchange exc) throws Exception {
                    exc.getResponse().getHeader().add("Connection", "close");
                    return Outcome.CONTINUE;
                }
            });
            serviceProxy.getInterceptors().add(dummyInterceptor);
            httpRouter.getRuleManager().addProxyAndOpenPortIfNew(serviceProxy);
            httpRouter.init();
        }

        balancer = new HttpRouter();
        ServiceProxy sp3 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 7000), "thomas-bayer.com", 80);
        balancingInterceptor = new LoadBalancingInterceptor();
        balancingInterceptor.setName("Default");
        sp3.getInterceptors().add(balancingInterceptor);
        balancer.getRuleManager().addProxyAndOpenPortIfNew(sp3);
        enableFailOverOn5XX(balancer);
        balancer.init();

        //add the destinations to the load balancer
        for (int i = 1; i <= ctx.numNodes; i++) {
            BalancerUtil.lookupBalancer(balancer, "Default").up("Default", "localhost", (2000 + i));
        }
    }

    private void enableFailOverOn5XX(HttpRouter balancer) {
        List<Interceptor> l = balancer.getTransport().getInterceptors();
        ((HTTPClientInterceptor) l.get(l.size() - 1)).setFailOverOn5XX(true);
    }

    @After
    public void tearDown() throws Exception {
        for (HttpRouter httpRouter : httpRouters) {
            httpRouter.shutdown();
        }
        balancer.shutdown();
    }


    /**
     * Because we set the success chance to 0, none will pass.
     */
    @Test
    public void test_2destinations_6threads_100calls_allFail() throws Exception {
        TestingContext ctx = new TestingContext.Builder()
                .numNodes(2)
                .numThreads(6)
                .numRequests(100)
                .successChance(0d)
                .build();

        run(ctx);
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
    public void test_2destinations_6threads_100calls_someFail() throws Exception {
        TestingContext ctx = new TestingContext.Builder()
                .numNodes(2)
                .numThreads(6)
                .numRequests(1000)
                .successChance(0.5d)
                .build();

        run(ctx);
        assertTrue(ctx.successCounter.get() >= 900);
        assertTrue(ctx.successCounter.get() < 1000);
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
                .preSubmitCallback(new Function<Integer, Void>() {
                    @Nullable
                    @Override
                    public Void apply(Integer integer) {
                        if (integer == 20) {
                            try {
                                httpRouters.get(0).shutdown();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        return null;
                    }
                })
                .build();

        run(ctx);

        assertEquals(100, ctx.successCounter.get());
        for (int i = 0; i < 100; i++) {
            if (i < 10 || i >= 40) {
                assertTrue("For " + i + " value was: " + ctx.runtimes[i], ctx.runtimes[i] < 500);
            }
        }
    }

    /**
     * After batches of 10 requests we terminate 4 of the 5 destinations.
     * Because not enough remain active, all destinations remain in the pool and are used by success rate chance.
     * But because 1 remains functional, in the end all requests succeed.
     */
    @Test
    public void test_5destinations_6threads_100calls_4shutdown() throws Exception {
        TestingContext ctx = new TestingContext.Builder()
                .numNodes(5)
                .numThreads(6)
                .numRequests(100)
                .successChance(1d)
                .preSubmitCallback(new Function<Integer, Void>() {
                    @Nullable
                    @Override
                    public Void apply(Integer integer) {
                        try {
                            if (integer == 10) {
                                httpRouters.get(0).shutdown();
                            } else if (integer == 20) {
                                httpRouters.get(1).shutdown();
                            } else if (integer == 30) {
                                httpRouters.get(2).shutdown();
                            } else if (integer == 40) {
                                httpRouters.get(3).shutdown();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                })
                .build();

        run(ctx);

        assertEquals(100, ctx.successCounter.get());
    }


    /**
     * Contains the variables used in one test run.
     */
    private static class TestingContext {

        static class Builder {
            private int numNodes = 1;
            private DispatchingStrategy dispatchingStrategy = FaultMonitoringStrategy.getInstance();
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
             * @param numThreads default is 6
             */
            public Builder numThreads(int numThreads) {
                this.numThreads = numThreads;
                return this;
            }

            /**
             * How many service requests to send in total (not per thread).
             * @param numRequests default is 100
             */
            public Builder numRequests(int numRequests) {
                this.numRequests = numRequests;
                return this;
            }

            /**
             * Each service request can be given a random chance to fail with a 5xx code.
             * See {@link RandomlyFailingDummyWebServiceInterceptor}.
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
        private final int numThreads;
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
            this.numThreads = numThreads;
            this.successChance = successChance;
            this.preSubmitCallback = preSubmitCallback;

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
            tpe.awaitTermination(10, TimeUnit.SECONDS);
        }

    }


    private void run(TestingContext ctx) throws Exception {
        setUp(ctx);
        balancingInterceptor.setDispatchingStrategy(ctx.dispatchingStrategy);

        Stopwatch overallTime = Stopwatch.createStarted();

        submitTasks(ctx);
        ctx.shutdown();

        long totalTimeSpent = overallTime.elapsed(TimeUnit.MILLISECONDS);
        System.out.println("Time spent total: " + totalTimeSpent + "ms, longest run was " + ctx.getSlowestRuntime() + "ms");

        standardExpectations(ctx);
    }


    private void submitTasks(final TestingContext ctx) {
        for (int i = 0; i < ctx.numRequests; i++) {
            if (ctx.preSubmitCallback != null) {
                ctx.preSubmitCallback.apply(i);
            }
            final int runNumber = i;
            ctx.tpe.submit(new Runnable() {
                @Override
                public void run() {
                    Stopwatch taskTime = Stopwatch.createStarted();
                    try {
                        ctx.runCounter.incrementAndGet();
                        final HttpClient client = new HttpClient();
                        client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
                        int responseCode = client.executeMethod(getPostMethod());
                        if (responseCode == 200) {
                            ctx.successCounter.incrementAndGet();
                        }
                    } catch (Exception e) {
                        ctx.exceptionCounter.incrementAndGet();
                        e.printStackTrace();
                    }
                    ctx.runtimes[runNumber] = taskTime.elapsed(TimeUnit.MILLISECONDS);
                }
            });
        }
    }


    private void standardExpectations(TestingContext ctx) {
        assertEquals(ctx.numRequests, ctx.runCounter.get());
        assertEquals(0, ctx.exceptionCounter.get());

        int totalInterceptorCount = 0;
        for (RandomlyFailingDummyWebServiceInterceptor dummyInterceptor : dummyInterceptors) {
            totalInterceptorCount += dummyInterceptor.getCount();
        }
        assertTrue(totalInterceptorCount >= ctx.numRequests); //there are more interceptor calls, one more for every failure.
    }


    /**
     * @param numThreads 1-n
     */
    private static ThreadPoolExecutor createThreadPoolExecutor(int numThreads) {
        return new ThreadPoolExecutor(
                numThreads, numThreads,
                1, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }


    private PostMethod getPostMethod() {
        PostMethod post = new PostMethod(
                "http://localhost:7000/axis2/services/BLZService");
        post.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream("/getBank.xml")));
        post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
        post.setRequestHeader(Header.SOAP_ACTION, "");
        return post;
    }

}
