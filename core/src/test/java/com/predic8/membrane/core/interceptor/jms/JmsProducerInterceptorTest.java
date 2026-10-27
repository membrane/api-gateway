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

package com.predic8.membrane.core.interceptor.jms;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static org.junit.jupiter.api.Assertions.*;

class JmsProducerInterceptorTest {

    static final String ARTEMIS = "org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory";

    static class RecordingProducer implements JmsProducerExtension {
        Map<String, String> properties;
        Exchange exchange;
        boolean stopped;
        IOException failure;

        @Override
        public void init(final Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public void send(final Exchange exc) throws IOException {
            if (failure != null)
                throw failure;
            this.exchange = exc;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    @TempDir
    Path tempDir;

    private final RecordingProducer producer = new RecordingProducer();

    private static JmsConnection connection() {
        final var connection = new JmsConnection();
        connection.setConnectionFactory(ARTEMIS);
        return connection;
    }

    private JmsProducerInterceptor interceptor() {
        final var i = new JmsProducerInterceptor() {
            @Override
            JmsProducerExtension createProducer() {
                return producer;
            }
        };
        i.setConnection(connection());
        return i;
    }

    @Test
    void missingExtensionFailsOnStartup() {
        final var i = new JmsProducerInterceptor();
        i.setConnection(connection());
        i.setQueue("orders");
        final var e = assertThrows(ConfigurationException.class, () -> i.init(new DummyTestRouter()));
        assertTrue(e.getMessage().contains("Membrane JMS Extension"));
    }

    @Test
    void needsConnection() {
        final var i = interceptor();
        i.setConnection(null);
        i.setQueue("orders");
        assertThrows(ConfigurationException.class, () -> i.init(new DummyTestRouter()));
    }

    @Test
    void needsQueueOrTopic() {
        final var i = interceptor();
        assertThrows(ConfigurationException.class, () -> i.init(new DummyTestRouter()));
    }

    @Test
    void rejectsQueueAndTopic() {
        final var i = interceptor();
        i.setQueue("orders");
        i.setTopic("order-events");
        assertThrows(ConfigurationException.class, () -> i.init(new DummyTestRouter()));
    }

    @Test
    void passesSettingsToExtension() {
        final var connection = connection();
        connection.setUsername("alice");
        connection.setFactoryProperties(Map.of("brokerURL", "tcp://mq.example.com:61616"));
        final var i = interceptor();
        i.setConnection(connection);
        i.setQueue("orders");
        i.setDeliveryMode(JmsProducerInterceptor.DeliveryMode.NON_PERSISTENT);
        i.init(new DummyTestRouter());
        assertEquals(Map.of(
                "connection.factory", ARTEMIS,
                "username", "alice",
                "factory.brokerURL", "tcp://mq.example.com:61616",
                "queue", "orders",
                "delivery.mode", "NON_PERSISTENT"), producer.properties);
    }

    @Test
    void passesTopicToExtension() {
        final var i = interceptor();
        i.setTopic("order-events");
        i.init(new DummyTestRouter());
        assertEquals("order-events", producer.properties.get("topic"));
        assertFalse(producer.properties.containsKey("queue"));
    }

    @Test
    void sendsExchange() throws Exception {
        final var i = interceptor();
        i.setQueue("orders");
        i.init(new DummyTestRouter());
        final var exc = post("/orders").body("{}").buildExchange();
        assertEquals(CONTINUE, i.handleRequest(exc));
        assertSame(exc, producer.exchange);
    }

    @Test
    void failedSendAnswersWith500() throws Exception {
        final var i = interceptor();
        i.setQueue("orders");
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
        i.setQueue("orders");
        i.init(new DummyTestRouter());
        producer.failure = new IOException(new ReadingBodyException("invalid gzip"));
        assertThrows(ReadingBodyException.class, () -> i.handleRequest(post("/orders").body("{}").buildExchange()));
    }

    @Test
    void destroyStopsExtension() {
        final var i = interceptor();
        i.setQueue("orders");
        i.init(new DummyTestRouter());
        i.destroy();
        assertTrue(producer.stopped);
    }

    @Test
    void reInitStopsPreviousExtension() {
        final var second = new RecordingProducer();
        final var i = new JmsProducerInterceptor() {
            private boolean first = true;

            @Override
            JmsProducerExtension createProducer() {
                if (!first)
                    return second;
                first = false;
                return producer;
            }
        };
        i.setConnection(connection());
        i.setQueue("orders");
        i.init(new DummyTestRouter());
        i.init(new DummyTestRouter());
        assertTrue(producer.stopped);
        assertFalse(second.stopped);
    }

    @Test
    void yamlInlineConnection() throws Exception {
        final var i = loadProducer("""
                api:
                  port: 2000
                  flow:
                    - jmsProducer:
                        jmsConnection:
                          connectionFactory: %s
                          username: alice
                          brokerURL: tcp://mq.example.com:61616
                          callTimeout: 30000
                        queue: orders
                """.formatted(ARTEMIS));
        assertEquals("orders", i.getQueue());
        assertEquals(ARTEMIS, i.getConnection().getConnectionFactory());
        assertEquals("alice", i.getConnection().getUsername());
        assertEquals(Map.of("brokerURL", "tcp://mq.example.com:61616", "callTimeout", "30000"), i.getConnection().getFactoryProperties());
    }

    @Test
    void yamlReferencedConnection() throws Exception {
        final var i = loadProducer("""
                components:
                  orderBroker:
                    jmsConnection:
                      connectionFactory: %s
                      brokerURL: tcp://mq.example.com:61616
                ---
                api:
                  port: 2000
                  flow:
                    - jmsProducer:
                        $ref: '#/components/orderBroker'
                        topic: order-events
                """.formatted(ARTEMIS));
        assertEquals("order-events", i.getTopic());
        assertEquals(ARTEMIS, i.getConnection().getConnectionFactory());
        assertEquals(Map.of("brokerURL", "tcp://mq.example.com:61616"), i.getConnection().getFactoryProperties());
    }

    private JmsProducerInterceptor loadProducer(final String yaml) throws Exception {
        final var config = tempDir.resolve("apis.yaml");
        Files.writeString(config, yaml);
        final var router = new DefaultRouter();
        try {
            loadIntoRouter(router, config.toString());
            return assertInstanceOf(JmsProducerInterceptor.class, router.getRuleManager().getRules().getFirst().getFlow().getFirst());
        } finally {
            router.stop();
        }
    }
}
