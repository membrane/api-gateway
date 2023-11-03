package com.predic8.membrane.examples.tests.opentelemetry;

import com.predic8.membrane.core.interceptor.opentelemetry.Traceparent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void parse() {
        List<Traceparent> traceparents = Traceparent.parse(HEADER);
        assertEquals(2,traceparents.size());
        assertEquals("00", traceparents.get(0).version);
        assertEquals("7b048dbe35a1cd06f55e037fb7ac095f", traceparents.get(0).traceId);
        assertEquals("a64b5de659a70718", traceparents.get(0).parentId);
        assertEquals("01", traceparents.get(0).flags);
    }
}
