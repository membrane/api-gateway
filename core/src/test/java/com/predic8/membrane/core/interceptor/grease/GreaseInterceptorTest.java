package com.predic8.membrane.core.interceptor.grease;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.grease.strategies.JsonGrease;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static java.lang.Math.ceil;
import static java.lang.Math.round;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SuppressWarnings("SameParameterValue")
public class GreaseInterceptorTest {
    private static GreaseInterceptor greaseInterceptor;
    private static final String json = "{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\"}";

    @BeforeAll
    static void setup() {
        greaseInterceptor = new GreaseInterceptor();
        greaseInterceptor.setRate(1);
        greaseInterceptor.setStrategies(List.of(new JsonGrease()));
    }

    @Test
    void detectJsonContentType() throws Exception {
        Exchange requestExc = new Request.Builder().contentType(APPLICATION_JSON).body(json).buildExchange();
        Exchange responseExc = new Exchange(null) {{
            setResponse(Response.ok().contentType(APPLICATION_JSON).body(json).build());
        }};
        greaseInterceptor.handleRequest(requestExc);
        greaseInterceptor.handleResponse(responseExc);
        assertNotEquals(json, requestExc.getRequest().getBodyAsStringDecoded());
        assertNotEquals(json, responseExc.getResponse().getBodyAsStringDecoded());
    }

    @Test
    public void testRate() throws Exception {
        Exchange requestExc = new Request.Builder().contentType("application/json").body(json).buildExchange();
        greaseInterceptor.handleRequest(requestExc);
        // Test with rate = 1
        assertNotEquals(json, requestExc.getRequest().getBodyAsStringDecoded());

        greaseInterceptor.setRate(0.1);
        assertEquals(0.1, calculateRate(greaseInterceptor, json), 0.02);

        greaseInterceptor.setRate(0.5);
        assertEquals(0.5, calculateRate(greaseInterceptor, json), 0.02);

        greaseInterceptor.setRate(0.01);
        assertEquals(0.01, calculateRate(greaseInterceptor, json), 0.02);
    }

    @Test
    public void testSetRate() {
        greaseInterceptor.setRate(0.5);
        assertEquals(0.5, greaseInterceptor.getRate());
        greaseInterceptor.setRate(1.5);
        assertEquals(1.0, greaseInterceptor.getRate());
        greaseInterceptor.setRate(-0.5);
        assertEquals(0.0, greaseInterceptor.getRate());
        greaseInterceptor.setRate(0.0001);
        assertEquals(0.0001, greaseInterceptor.getRate());
    }

    private double calculateRate(GreaseInterceptor interceptor, String json) throws Exception {
        int executedCount = 0;
        for (int i = 0; i < 10000; i++) {
            Exchange tmp = new Request.Builder().contentType(APPLICATION_JSON).body(json).buildExchange();
            interceptor.handleRequest(tmp);
            String result = tmp.getRequest().getBodyAsStringDecoded();
            if (!Objects.equals(result, json)) {
                executedCount++;
            }
        }
        return ((double) executedCount / 10000);
    }

}

