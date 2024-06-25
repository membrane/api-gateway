/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.grease;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.grease.strategies.JsonGrease;
import com.predic8.membrane.core.util.Pair;
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
class GreaseInterceptorTest {
    private static GreaseInterceptor greaseInterceptor;
    private static final String json = """
            {"a":"1","b":"2","c":"3"}""";

    @BeforeEach
    void setup() {
        greaseInterceptor = new GreaseInterceptor();
        greaseInterceptor.setRate(1);
        greaseInterceptor.setStrategies(List.of(new JsonGrease() {{
            setAdditionalProperties(false);}}));
    }

    @Test
    void detectJsonContentType() throws Exception {
        var result = getDoShuffle();
        assertNotEquals(json, result.first().getRequest().getBodyAsStringDecoded());
        assertNotEquals(json, result.second().getResponse().getBodyAsStringDecoded());
    }

    @Test
    void correctChangeHeader() throws Exception {
        var result = getDoShuffle();
        assertNotEquals("X-GREASE: JSON fields shuffled, Added random JSON fields", result.first().getRequest().getHeader().getFirstValue(X_GREASE));
        assertNotEquals(json, result.second().getResponse().getHeader().getFirstValue(X_GREASE));
    }

    @Test
    void testRate() throws Exception {
        Exchange requestExc = new Request.Builder().contentType(APPLICATION_JSON).body(json).buildExchange();
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
    void testSetRate() {
        greaseInterceptor.setRate(0.5);
        assertEquals(0.5, greaseInterceptor.getRate());
        greaseInterceptor.setRate(1.5);
        assertEquals(1.0, greaseInterceptor.getRate());
        greaseInterceptor.setRate(-0.5);
        assertEquals(0.0, greaseInterceptor.getRate());
        greaseInterceptor.setRate(0.0001);
        assertEquals(0.0001, greaseInterceptor.getRate());
    }

    private static @NotNull Pair<Exchange,Exchange> getDoShuffle() throws Exception {
        Exchange requestExc = new Request.Builder().contentType(APPLICATION_JSON).body(json).buildExchange();
        Exchange responseExc = new Exchange(null) {{
            setResponse(Response.ok().contentType(APPLICATION_JSON).body(json).build());
        }};
        greaseInterceptor.handleRequest(requestExc);
        greaseInterceptor.handleResponse(responseExc);
        return new Pair<>(requestExc, responseExc);
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

