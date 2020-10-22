/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.services;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Like the {@link DummyWebServiceInterceptor}, and each call has a chance to fail with a 5xx code.
 */
public class RandomlyFailingDummyWebServiceInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(RandomlyFailingDummyWebServiceInterceptor.class.getName());

    private AtomicLong counter = new AtomicLong();
    private final double successChance;

    /**
     *
     * @param successChance [0,1] where 0.8 means 80% shall pass.
     *                      The specials value 0.0 means none and 1.0 means all will pass.
     */
    public RandomlyFailingDummyWebServiceInterceptor(double successChance) {
        if (successChance < 0d || successChance > 1d) {
            throw new IllegalArgumentException("Success chance must be [0,1] but was: "+ successChance);
        }
        this.successChance = successChance;
    }

    @Override
	public Outcome handleRequest(Exchange exc) throws Exception {
        long count = counter.incrementAndGet();
        log.debug("handle request " + count);

        //this generates [0,1} (excluding 1.0)
        //this way we can safely use the special value 1.0 for 'always passing' without the need of an extra check.
        double random = ThreadLocalRandom.current().nextDouble(1d);
        if (random >= successChance) {
            exc.setResponse(Response.internalServerError("Random failure").build());
        } else {
            exc.setResponse(Response.ok().contentType("text/html").body("<aaa></aaa>".getBytes()).build());
        }

		return Outcome.RETURN;
	}

   public long getCount() {
      return counter.get();
   }

}
