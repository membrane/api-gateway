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
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class KafkaProducerInterceptorTest {

    static class RecordingProducer implements KafkaProducerExtension {
        Map<String, String> properties;
        String topic;
        byte[] key;
        Exchange exchange;
        boolean stopped;
        IOException failure;

        @Override
        public void init(final Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public void send(final String topic, final byte[] key, final Exchange exc) throws IOException {
            if (failure != null)
                throw failure;
            this.topic = topic;
            this.key = key;
            this.exchange = exc;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    private final RecordingProducer producer = new RecordingProducer();

    private KafkaProducerInterceptor interceptor() {
        final var i = new KafkaProducerInterceptor() {
            @Override
            KafkaProducerExtension createProducer() {
                return producer;
            }
        };
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        return i;
    }

    @Test
    void missingExtensionFailsOnStartup() {
        final var i = new KafkaProducerInterceptor();
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        final var e = assertThrows(ConfigurationException.class, () -> i.init(new DummyTestRouter()));
        assertTrue(e.getMessage().contains("Membrane Kafka Extension"));
    }

    @Test
    void passesSettingsToExtension() {
        final var i = interceptor();
        i.setClientId("membrane");
        i.setLingerMs(5);
        i.init(new DummyTestRouter());
        assertEquals("localhost:9092", producer.properties.get("bootstrap.servers"));
        assertEquals("membrane", producer.properties.get("client.id"));
        assertEquals("5", producer.properties.get("linger.ms"));
        assertFalse(producer.properties.containsKey("security.protocol"));
    }

    @Test
    void sendsWithoutKey() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        final var exc = post("/orders").body("{}").buildExchange();
        assertEquals(CONTINUE, i.handleRequest(exc));
        assertEquals("orders", producer.topic);
        assertNull(producer.key);
        assertSame(exc, producer.exchange);
    }

    @Test
    void sendsWithKey() throws Exception {
        final var i = interceptor();
        i.setKey("headers['X-Order-Id']");
        i.init(new DummyTestRouter());
        assertEquals(CONTINUE, i.handleRequest(post("/orders").header("X-Order-Id", "42").body("{}").buildExchange()));
        assertArrayEquals("42".getBytes(UTF_8), producer.key);
    }

    @Test
    void failedSendAnswersWith500() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        producer.failure = new IOException("broker down");
        final var exc = post("/orders").body("{}").buildExchange();
        assertEquals(ABORT, i.handleRequest(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    /**
     * FlowController answers it with 400, it is no broker failure.
     */
    @Test
    void unreadableBodyIsLeftToFlowController() {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        producer.failure = new IOException(new ReadingBodyException("invalid gzip"));
        assertThrows(ReadingBodyException.class, () -> i.handleRequest(post("/orders").body("{}").buildExchange()));
    }

    @Test
    void destroyStopsExtension() {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        i.destroy();
        assertTrue(producer.stopped);
    }

    @Test
    void reInitStopsPreviousExtension() {
        final var second = new RecordingProducer();
        final var i = new KafkaProducerInterceptor() {
            private boolean first = true;

            @Override
            KafkaProducerExtension createProducer() {
                if (!first)
                    return second;
                first = false;
                return producer;
            }
        };
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        i.init(new DummyTestRouter());
        i.init(new DummyTestRouter());
        assertTrue(producer.stopped);
        assertFalse(second.stopped);
    }
}
