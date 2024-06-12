package com.predic8.membrane.core.interceptor.grease;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.grease.strategies.JsonGrease;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.grease.GreaseInterceptor.X_GREASE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SuppressWarnings("SameParameterValue")
public class GreaseInterceptorTest {
    private static GreaseInterceptor greaseInterceptor;
    private static final String json = "{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\"}";

    @BeforeEach
    void setup() {
        greaseInterceptor = new GreaseInterceptor();
        greaseInterceptor.setRate(1);
        greaseInterceptor.setStrategies(List.of(new JsonGrease() {{setAddAdditionalFields(false);}}));
    }

    @Test
    void detectJsonContentType() throws Exception {
        shuffleExchanges result = getDoShuffle();
        assertNotEquals(json, result.requestExc().getRequest().getBodyAsStringDecoded());
        assertNotEquals(json, result.responseExc().getResponse().getBodyAsStringDecoded());
    }

    @Test
    void correctChangeHeader() throws Exception {
        shuffleExchanges result = getDoShuffle();
        assertNotEquals("X-GREASE: JSON fields shuffled, Added random JSON fields", result.requestExc().getRequest().getHeader().getFirstValue(X_GREASE));
        assertNotEquals(json, result.responseExc().getResponse().getHeader().getFirstValue(X_GREASE));
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

    private static @NotNull shuffleExchanges getDoShuffle() throws Exception {
        Exchange requestExc = new Request.Builder().contentType(APPLICATION_JSON).body(json).buildExchange();
        Exchange responseExc = new Exchange(null) {{
            setResponse(Response.ok().contentType(APPLICATION_JSON).body(json).build());
        }};
        greaseInterceptor.handleRequest(requestExc);
        greaseInterceptor.handleResponse(responseExc);
        return new shuffleExchanges(requestExc, responseExc);
    }

    private record shuffleExchanges(Exchange requestExc, Exchange responseExc) {
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

