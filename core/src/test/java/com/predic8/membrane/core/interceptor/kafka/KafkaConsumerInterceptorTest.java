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

package com.predic8.membrane.core.interceptor.kafka;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.InternalProxy;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.KEY_PROPERTY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaConsumerInterceptorTest {

    private static KafkaConsumerInterceptor consumer(final String forward) {
        final var i = new KafkaConsumerInterceptor();
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        i.setForward(forward);
        return i;
    }

    @Test
    void initWithDummyConsumer() {
        consumer("internal://handler").init(new DummyTestRouter());
    }

    @Test
    void forwardIsRequired() {
        final var i = new KafkaConsumerInterceptor();
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        assertThrows(ConfigurationException.class, () -> i.init(new DummyTestRouter()));
    }

    @Test
    void destroyStopsConsumer() {
        final var i = consumer("internal://handler");
        i.init(new DummyTestRouter());
        i.destroy();
    }

    @Test
    void forwardMustBeInternal() {
        assertThrows(ConfigurationException.class, () -> consumer("http://example.com").init(new DummyTestRouter()));
    }

    @Test
    void failedForwardIsReported() throws Exception {
        final var router = new TestRouter();
        final var internal = new InternalProxy();
        internal.setName("failing");
        internal.setFlow(List.of(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(final Exchange exc) {
                exc.setResponse(Response.internalServerError().build());
                return Outcome.RETURN;
            }
        }));
        router.add(internal);

        final var i = consumer("internal://failing");
        i.init(router);
        assertThrows(IllegalStateException.class, () -> i.forward(post("/").body("hello").buildExchange()));
    }

    @Test
    void forwardsMessageToInternalApi() throws Exception {
        final var bodies = new ArrayList<String>();
        final var keys = new ArrayList<String>();

        final var router = new TestRouter();
        final var internal = new InternalProxy();
        internal.setName("handler");
        internal.setFlow(List.of(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(final Exchange exc) {
                bodies.add(exc.getRequest().getBodyAsStringDecoded());
                keys.add(new String(exc.getProperty(KEY_PROPERTY, byte[].class), UTF_8));
                return CONTINUE;
            }
        }));
        router.add(internal);

        final var i = consumer("internal://handler");
        i.init(router);
        final var exc = post("/").body("hello").buildExchange();
        exc.setProperty(KEY_PROPERTY, "k1".getBytes(UTF_8));
        i.forward(exc);

        assertEquals(List.of("hello"), bodies);
        assertEquals(List.of("k1"), keys);
    }
}
