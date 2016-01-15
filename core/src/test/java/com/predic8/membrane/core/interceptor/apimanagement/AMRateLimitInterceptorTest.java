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
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apimanagement.rateLimiter.AMRateLimiter;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class AMRateLimitInterceptorTest {

    @Test
    public void testHandleRequestRateLimit5SecondConcurrency() throws Exception {


        final Exchange exc = new Exchange(null);
        exc.setResponse(Response.ResponseBuilder.newInstance().build());
        exc.setProperty(Exchange.API_KEY,"junit");
        exc.setRule(new ServiceProxy());
        exc.getRule().setName("junit API");

        final AMRateLimiter rli = new AMRateLimiter();
        ApiManagementConfiguration amc = new ApiManagementConfiguration(System.getProperty("user.dir") , "src\\test\\resources\\apimanagement\\api.yaml");
        rli.setAmc(amc);

        ArrayList<Thread> threads = new ArrayList<Thread>();
        final AtomicInteger continues = new AtomicInteger();
        final AtomicInteger returns = new AtomicInteger();
        for(int i = 0; i < 1000; i++)
        {
            Thread t = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        Outcome out = rli.handleRequest(exc);
                        if(out == Outcome.CONTINUE)
                        {
                            continues.incrementAndGet();
                        }
                        else if(out == Outcome.RETURN)
                        {
                            returns.incrementAndGet();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            threads.add(t);
            t.start();
        }
        for(Thread t : threads)
        {
            t.join();
        }
        assertEquals(5, continues.get());
        assertEquals(995, returns.get());
        Thread.sleep(2000);
        assertEquals(Outcome.CONTINUE,rli.handleRequest(exc));
    }
}
