/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class StatisticCollectorTest {

    @Test
    void numericTimesAreMinusOneWithoutExchanges() {
        final var sc = new StatisticCollector(true);
        assertEquals(-1, sc.getMinTimeMillis());
        assertEquals(-1, sc.getMaxTimeMillis());
        assertEquals(-1, sc.getAvgTimeMillis());
        assertEquals(0, sc.getErrors());
    }

    @Test
    void numericTimes() throws Exception {
        final var sc = new StatisticCollector(true);
        sc.collectFrom(exchange(100, 110));
        sc.collectFrom(exchange(100, 130));

        assertEquals(10, sc.getMinTimeMillis());
        assertEquals(30, sc.getMaxTimeMillis());
        assertEquals(20.0, sc.getAvgTimeMillis());
        assertEquals(2, sc.getCount());
    }

    private static com.predic8.membrane.core.exchange.Exchange exchange(long reqSent, long resSent) throws Exception {
        final var exc = Request.get("/").buildExchange();
        exc.setResponse(Response.ok().build());
        exc.setTimeReqSent(reqSent);
        exc.setTimeResSent(resSent);
        return exc;
    }
}
