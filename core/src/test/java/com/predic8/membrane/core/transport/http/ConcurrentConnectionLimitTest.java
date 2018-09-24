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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class ConcurrentConnectionLimitTest {

    private HttpRouter router;
    private ExecutorService executor;
    private int concurrency = 500;
    private int concurrentLimit;
    private CountDownLatch countDownLatchStart = new CountDownLatch(concurrency);
    private CountDownLatch countDownLatchEnd = new CountDownLatch(concurrency);
    private int port = 3026;

    @Before
    public void setup() throws Exception{
        executor = Executors.newFixedThreadPool(concurrency);

        router = new HttpRouter();
        concurrentLimit = router.getTransport().getConcurrentConnectionLimitPerIp();

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", port), "", -1);

        GroovyInterceptor gi = new GroovyInterceptor();
        gi.setSrc("exc.setResponse(Response.ok(\"Response\").build())\nRETURN");
        sp.getInterceptors().add(gi);

        router.getRuleManager().addProxyAndOpenPortIfNew(sp);
        router.init();
    }

    @After
    public void tearDown() throws Exception {
        router.shutdown();
    }

    @Test
    public void testConcurrentConnectionsLimit() throws Exception{
        List<Integer> good = new ArrayList<>();
        List<Integer> bad = new ArrayList<>();
        IntStream.range(0,concurrency).forEach(i -> {
            executor.execute(() -> {
                try {
                    Thread.currentThread().setName("Test Thread " + i);
                    countDownLatchStart.countDown();
                    System.out.println("start = " + countDownLatchStart.getCount());
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
                    System.out.println("end = " + countDownLatchEnd.getCount());
                    countDownLatchEnd.await();
                    con.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        bad.stream().distinct().forEach(code -> assertEquals("All bad responses are 429",429,code.intValue()));
        assertEquals("Number of bad responses",concurrency-concurrentLimit,bad.size());
        assertEquals("Number of good responses",concurrentLimit,good.size());
    }
}
