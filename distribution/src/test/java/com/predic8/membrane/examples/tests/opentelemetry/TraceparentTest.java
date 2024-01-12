/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.tests.opentelemetry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceparentTest {

    private final String HEADER = "Request headers:\n" +
                                  "Accept: */*\n" +
                                  "Host: localhost:3000\n" +
                                  "Connection: Keep-Alive\n" +
                                  "User-Agent: Apache-HttpClient/4.5.14 (Java/17.0.1)\n" +
                                  "Accept-Encoding: gzip,deflate\n" +
                                  "X-Forwarded-For: 127.0.0.1, 127.0.0.1\n" +
                                  "X-Forwarded-Proto: http\n" +
                                  "X-Forwarded-Host: localhost:2000, localhost:3000\n" +
                                  "traceparent: 00-7b048dbe35a1cd06f55e037fb7ac095f-a64b5de659a70718-01\n" +
                                  "traceparent: 00-7b048dbe35a1cd06f55e037fb7ac095f-21746733979d7a8b-01";
    List<Traceparent> traceparents = Traceparent.parse(HEADER);


    @Test
    void parse() {
        assertEquals(2,traceparents.size());
        assertEquals("00", traceparents.get(0).version);
        assertEquals("7b048dbe35a1cd06f55e037fb7ac095f", traceparents.get(0).traceId);
        assertEquals("a64b5de659a70718", traceparents.get(0).parentId);
        assertEquals("01", traceparents.get(0).flags);
    }

    @Test
    void testCompareTraceId() {
        assertTrue(traceparents.get(0).sameTraceId(traceparents.get(1)));
    }
}
