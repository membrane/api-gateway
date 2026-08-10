/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentConnectionLimitTest {

    private Router router;
    private ExecutorService executor;
    private final int concurrency = 100;      // total simultaneous client connections fired at the backend
    private final int concurrentLimit = 10;   // per-IP limit configured on the transport
    // Barrier so all `concurrency` client threads issue their HTTP request at (as close to)
    // the same instant as possible - the test needs a genuine burst, not a trickle, to exercise
    // the limiter's behavior under real concurrency rather than sequential calls.
    private final CountDownLatch countDownLatchStart = new CountDownLatch(concurrency);
    // Barrier so no client disconnects until every client has received a response - this stops
    // early disconnects from freeing up a connection slot (and thus admitting a late connection)
    // before all 100 attempts have been resolved by the server.
    private final CountDownLatch countDownLatchEnd = new CountDownLatch(concurrency);
    private final int port = 3026;

    @BeforeEach
    public void setup() throws Exception{
        executor = Executors.newFixedThreadPool(concurrency);

        router = new TestRouter();

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", port), "", -1);

        // Hold each admitted connection "busy" for 1s. This gives the single accept-thread in
        // HttpEndpointListener a full second to accept/reject all `concurrency` connections
        // before any admitted ("good") connection finishes and frees its slot - without this
        // margin, an early completion could let a 101st-in-line connection sneak in as "good".
        sp.getFlow().add(GROOVY("Thread.sleep(1000)"));
        sp.getFlow().add(RETURN);

        router.add(sp);
        // The default TCP accept backlog (50, HttpTransport.backlog) is smaller than the
        // `concurrency` burst (100) this test fires. When the backlog is exceeded, the OS queues
        // /paces the excess SYNs instead of making them all available to accept() at once, which
        // spreads acceptance across multiple 1s admission windows and lets more than
        // `concurrentLimit` connections succeed. Match the backlog to the burst size so the
        // whole burst lands in one tight accept() loop, before any "good" connection's 1s sleep
        // elapses - this is what makes the assertions below deterministic.
        ((HttpTransport) router.getTransport()).setBacklog(concurrency);
        router.start();
        router.getTransport().setConcurrentConnectionLimitPerIp(concurrentLimit);
    }

    @AfterEach
    public void tearDown() throws Exception {
        router.stop();
    }

    @Test
    public void testConcurrentConnectionsLimit() throws Exception{
        List<Integer> good = new ArrayList<>();
        List<Integer> bad = new ArrayList<>();
        IntStream.range(0,concurrency).forEach(i -> executor.execute(() -> {
            try {
                Thread.currentThread().setName("Test Thread " + i);
                // Wait until all `concurrency` threads have reached this point, then release
                // together - this is what turns 100 independent requests into one real burst.
                countDownLatchStart.countDown();
                countDownLatchStart.await();
                HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:" + port).openConnection();
                int code = 429; // default to "rejected" if the connection fails outright (e.g. reset)
                try {
                    code = con.getResponseCode();
                }catch (Exception e){
                    //ignored)
                }finally {
                    if (code == 200)
                        synchronized (good) {
                            good.add(code);
                        }
                    else
                        synchronized (bad) {
                            bad.add(code);
                        }
                }
                // Don't disconnect until every thread has recorded its response - an early
                // disconnect would free a connection slot mid-test and let a still-pending
                // attempt succeed, corrupting the good/bad counts this test asserts on.
                countDownLatchEnd.countDown();
                countDownLatchEnd.await();
                con.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        bad.stream().distinct().forEach(code -> assertEquals(429, code.intValue(), "All bad responses are 429"));
        // Exactly `concurrentLimit` of the burst should be admitted, the rest rejected with 429 -
        // this is the actual behavior under test: the per-IP limiter enforces a hard, precise cap
        // under genuine concurrent load, not just for sequential requests.
        assertEquals(concurrency - concurrentLimit, bad.size(), "Number of bad responses");
        assertEquals(concurrentLimit, good.size(), "Number of good responses");
    }
}
