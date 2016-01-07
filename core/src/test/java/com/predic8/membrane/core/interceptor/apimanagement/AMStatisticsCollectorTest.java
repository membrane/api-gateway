/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
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
import com.predic8.membrane.core.interceptor.apimanagement.statistics.AMStatisticsCollector;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.junit.Test;

import java.util.ArrayList;

public class AMStatisticsCollectorTest {

    @Test
    public void testThreadedStatisticCollection() throws InterruptedException {

        final AMStatisticsCollector amSc = new AMStatisticsCollector();
        ArrayList<Thread> threads = new ArrayList<Thread>();

        for (int i = 0; i < 1000; i++) {
            final int j = i;
            Thread t = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        Exchange exc = new Exchange(null);
                        exc.setRequest(new Request.Builder().build());
                        exc.setResponse(new Response.ResponseBuilder().build());
                        exc.setProperty(Exchange.API_KEY, "junit-" + j);
                        exc.setRule(new ServiceProxy());
                        exc.getRule().setName("junit API");
                        for(int k = 0; k < 10; k++){
                            amSc.addExchangeToQueue(exc);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }


        Thread.sleep(amSc.getCollectTimeInSeconds() * 3 * 1000);


    }


}
