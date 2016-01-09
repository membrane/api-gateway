/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.apimanagement;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apimanagement.quota.AMQuota;
import com.predic8.membrane.core.interceptor.apimanagement.rateLimiter.AMRateLimiter;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class AMQuotaInterceptorTest {
    @Test
    public void testAMQuota() throws IOException, InterruptedException {
        final Exchange exc = new Exchange(null);
        exc.setRequest(new Request.Builder().header("Test","test").body("hello").build());
        exc.setResponse(new Response.ResponseBuilder().header("Test2","test2").body("Hello back!").build());
        exc.setProperty(Exchange.API_KEY,"junit");
        exc.setRule(new ServiceProxy());
        exc.getRule().setName("junit API");

        ApiManagementConfiguration amc = new ApiManagementConfiguration(System.getProperty("user.dir") , "src\\test\\resources\\apimanagement\\api.yaml");

        int reqSize = exc.getRequest().getHeader().toString().getBytes().length+exc.getRequest().getHeader().getContentLength();
        int respSize = exc.getResponse().getHeader().toString().getBytes().length+exc.getResponse().getHeader().getContentLength();

        assertEquals(31+5,reqSize);
        assertEquals(34+11,respSize);

        final AMQuota amq = new AMQuota();
        amq.setAmc(amc);

        ArrayList<Thread> threads = new ArrayList<Thread>();
        final AtomicInteger continues = new AtomicInteger();
        final AtomicInteger returns = new AtomicInteger();
        for(int i = 0; i < 1000; i++)
        {
            Thread t = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        Outcome out = amq.handleRequest(exc);
                        if(out == Outcome.CONTINUE)
                        {
                            continues.incrementAndGet();
                        }
                        else if(out == Outcome.RETURN)
                        {
                            returns.incrementAndGet();
                        }
                        amq.handleResponse(exc);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            threads.add(t);
            //t.start();
            t.run(); // doing sync because else we cant predictably count request/response pairs
        }
        for(Thread t : threads)
        {
            t.join();
        }
        // the limit is ( or should be ) 120B
        // 31+5 ( Req ) + 34+11 ( Resp ) = 81 for every completed exchange
        // the second request adds another 31+5 -> 81 + 36 = 117 < 120B -> after the second request it should block because the limit is 120b and the following response would bring it over the limit ( responses never block, only requests )
        assertEquals(2, continues.get());
        assertEquals(998, returns.get());
        Thread.sleep(2000);
        assertEquals(Outcome.CONTINUE,amq.handleRequest(exc));

    }
}
