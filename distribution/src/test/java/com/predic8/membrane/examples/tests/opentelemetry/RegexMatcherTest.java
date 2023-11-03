package com.predic8.membrane.examples.tests.opentelemetry;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static java.util.regex.Pattern.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RegexMatcherTest {

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
    public void testRegexMatching() {

        Matcher m = compile("traceparent: (.*)-(.*)-(.*)-(.*)").matcher(HEADER);

        if (m.find()) assertEquals("7b048dbe35a1cd06f55e037fb7ac095f", m.group(2));
    }
}
