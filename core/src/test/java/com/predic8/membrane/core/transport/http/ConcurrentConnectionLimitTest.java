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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentConnectionLimitTest {

    private HttpRouter router;
    private ExecutorService executor;
    private int concurrency = 100;
    private int concurrentLimit = 10;
    private CountDownLatch countDownLatchStart = new CountDownLatch(concurrency);
    private CountDownLatch countDownLatchEnd = new CountDownLatch(concurrency);
    private int port = 3026;

    @BeforeEach
    public void setup() throws Exception{
        executor = Executors.newFixedThreadPool(concurrency);

        router = new HttpRouter();
        router.getTransport().setConcurrentConnectionLimitPerIp(concurrentLimit);

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", port), "", -1);

        sp.getInterceptors().add(GROOVY("Thread.sleep(1000)"));
        sp.getInterceptors().add(RETURN);

        router.getRuleManager().addProxyAndOpenPortIfNew(sp);
        router.init();
    }

    @AfterEach
    public void tearDown() throws Exception {
        router.shutdown();
    }

    @Test
    public void testConcurrentConnectionsLimit() throws Exception{
        List<Integer> good = new ArrayList<>();
        List<Integer> bad = new ArrayList<>();
        IntStream.range(0,concurrency).forEach(i -> executor.execute(() -> {
            try {
                Thread.currentThread().setName("Test Thread " + i);
                countDownLatchStart.countDown();
                countDownLatchStart.await();
                HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:" + port).openConnection();
                int code = 429;
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
        assertEquals(concurrency - concurrentLimit, bad.size(), "Number of bad responses");
        assertEquals(concurrentLimit, good.size(), "Number of good responses");
    }
}
