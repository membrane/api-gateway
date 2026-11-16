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
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.predic8.membrane.core.exchange.ExchangeState.COMPLETED;
import static com.predic8.membrane.core.exchange.ExchangeState.FAILED;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.jms.JmsConsumerExtension.Result.ACK;
import static com.predic8.membrane.core.interceptor.jms.JmsConsumerExtension.Result.ROLLBACK;
import static org.junit.jupiter.api.Assertions.*;

class JmsConsumerProxyTest {

    private Router router;

    /**
     * Request the backend received last, written by the backend's request thread.
     */
    private volatile Request received;
    private volatile String receivedBody;

    @AfterEach
    void tearDown() {
        if (router != null)
            router.stop();
    }

    /**
     * Stands in for the Membrane JMS Extension and keeps the handler, so a test can deliver a message.
     */
    private static class StubConsumer implements JmsConsumerExtension {
        JmsConsumerExtension.MessageHandler handler;
        Map<String, String> properties;
        int apiVersion = API_VERSION;
        boolean stopped;

        @Override
        public int apiVersion() {
            return apiVersion;
        }

        @Override
        public void init(final Router router, final Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public void start(final MessageHandler handler) {
            this.handler = handler;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    private static JmsConnection connection() {
        final var connection = new JmsConnection();
        connection.setConnectionFactory("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory");
        return connection;
    }

    private static JmsConsumerProxy consumer(final StubConsumer stub, final String targetUrl) {
        final var proxy = new JmsConsumerProxy() {
            @Override
            JmsConsumerExtension createConsumer() {
                return stub;
            }
        };
        proxy.setConnection(connection());
        proxy.setQueue("orders");
        proxy.getTarget().setUrl(targetUrl);
        return proxy;
    }

    private APIProxy backend(final String path, final int status) {
        final var api = new APIProxy();
        api.setKey(new APIProxyKey(2000));
        api.getKey().setPath(path);
        api.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(final Exchange exc) {
                received = exc.getRequest();
                receivedBody = exc.getRequest().getBodyAsStringDecoded();
                return CONTINUE;
            }
        });
        final var ret = new ReturnInterceptor();
        ret.setStatus(status);
        api.getFlow().add(ret);
        return api;
    }

    /**
     * Router with normal APIs on localhost:2000, /ok returns 200, /bad returns 400 and /fail returns 500.
     */
    private void startRouter(final JmsConsumerProxy consumer) throws Exception {
        router = new TestRouter();
        router.add(backend("/ok", 200));
        router.add(backend("/bad", 400));
        router.add(backend("/fail", 500));
        router.add(consumer);
        router.start();
    }

    /**
     * A message as the extension creates it, see {@link JmsConsumerExtension.MessageHandler}.
     */
    private static Exchange message() throws Exception {
        return Request.post("/").body("order").buildExchange();
    }

    private static DummyTestRouter dummyRouter() {
        return new DummyTestRouter();
    }

    @Test
    void deliversMessageThroughFlowToTarget() throws Exception {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        consumer.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(final Exchange exc) {
                exc.getRequest().getHeader().setValue("X-Source", "jms");
                return CONTINUE;
            }
        });
        startRouter(consumer);

        final var exc = message();
        assertEquals(ACK, stub.handler.handle(exc));

        assertEquals("/ok", received.getUri());
        assertEquals("jms", received.getHeader().getFirstValue("X-Source"));
        assertEquals("order", receivedBody);
    }

    @Test
    void serverErrorOfTargetRollsBack() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2000/fail"));

        assertEquals(ROLLBACK, stub.handler.handle(message()));
    }

    @Test
    void clientErrorOfTargetRollsBack() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2000/bad"));

        assertEquals(ROLLBACK, stub.handler.handle(message()));
    }

    @Test
    void unreachableTargetRollsBack() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2001/down"));

        assertEquals(ROLLBACK, stub.handler.handle(message()));
    }

    @Test
    void readsResponseAndCompletesExchange() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2000/ok"));

        final var exc = message();
        assertEquals(ACK, stub.handler.handle(exc));

        assertTrue(exc.getResponse().getBody().isRead());
        assertEquals(COMPLETED, exc.getStatus());
    }

    @Test
    void readsResponseAndFailsExchangeOnErrorStatus() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2000/fail"));

        final var exc = message();
        assertEquals(ROLLBACK, stub.handler.handle(exc));

        assertTrue(exc.getResponse().getBody().isRead());
        assertEquals(FAILED, exc.getStatus());
    }

    @Test
    void passesSettingsToExtension() {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        consumer.getConnection().setUsername("membrane");
        consumer.setConcurrency(4);
        consumer.init(dummyRouter());

        assertEquals("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory", stub.properties.get("connection.factory"));
        assertEquals("membrane", stub.properties.get("username"));
        assertEquals("orders", stub.properties.get("queue"));
        assertEquals("4", stub.properties.get("concurrency"));
        assertFalse(stub.properties.containsKey("topic"));
        assertFalse(stub.properties.containsKey("subscription"));
        assertNotNull(stub.handler);
    }

    @Test
    void topicWithSubscription() {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        consumer.setQueue(null);
        consumer.setTopic("order-events");
        consumer.setSubscription("membrane");
        consumer.init(dummyRouter());

        assertEquals("order-events", stub.properties.get("topic"));
        assertEquals("membrane", stub.properties.get("subscription"));
        assertEquals("order-events", consumer.getName());
    }

    /**
     * Every session would receive its own copy of every message of the topic.
     */
    @Test
    void topicWithConcurrencyNeedsSubscription() {
        final var consumer = consumer(new StubConsumer(), "http://localhost:2000/ok");
        consumer.setQueue(null);
        consumer.setTopic("order-events");
        consumer.setConcurrency(2);

        assertThrows(ConfigurationException.class, () -> consumer.init(dummyRouter()));
    }

    @Test
    void subscriptionNeedsTopic() {
        final var consumer = consumer(new StubConsumer(), "http://localhost:2000/ok");
        consumer.setSubscription("membrane");

        assertThrows(ConfigurationException.class, () -> consumer.init(dummyRouter()));
    }

    @Test
    void queueXorTopic() {
        final var neither = consumer(new StubConsumer(), "http://localhost:2000/ok");
        neither.setQueue(null);
        assertThrows(ConfigurationException.class, () -> neither.init(dummyRouter()));

        final var both = consumer(new StubConsumer(), "http://localhost:2000/ok");
        both.setTopic("order-events");
        assertThrows(ConfigurationException.class, () -> both.init(dummyRouter()));
    }

    @Test
    void missingConnection() {
        final var consumer = consumer(new StubConsumer(), "http://localhost:2000/ok");
        consumer.setConnection(null);

        assertThrows(ConfigurationException.class, () -> consumer.init(dummyRouter()));
    }

    @Test
    void concurrencyMustBePositive() {
        final var consumer = consumer(new StubConsumer(), "http://localhost:2000/ok");
        consumer.setConcurrency(0);

        assertThrows(ConfigurationException.class, () -> consumer.init(dummyRouter()));
    }

    @Test
    void retryInitIsRejected() {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        final var dummy = dummyRouter();
        dummy.getConfiguration().setRetryInit(true);

        // With retryInit the router catches the exception and keeps the API inactive
        consumer.init(dummy);

        assertFalse(consumer.isActive());
        assertTrue(consumer.getErrorState().contains("retryInit"), consumer.getErrorState());
        assertNull(stub.handler);
    }

    @Test
    void incompatibleExtensionVersionFailsOnStartup() {
        final var stub = new StubConsumer();
        stub.apiVersion = JmsConsumerExtension.API_VERSION + 1;
        final var consumer = consumer(stub, "http://localhost:2000/ok");

        final var e = assertThrows(ConfigurationException.class, () -> consumer.init(dummyRouter()));
        assertTrue(e.getMessage().contains("API version"));
        assertNull(stub.properties);
    }

    @Test
    void missingExtensionFailsOnStartup() {
        final var consumer = new JmsConsumerProxy();
        consumer.setConnection(connection());
        consumer.setQueue("orders");
        consumer.getTarget().setUrl("http://localhost:2000/ok");

        final var e = assertThrows(ConfigurationException.class, () -> consumer.init(dummyRouter()));
        assertTrue(e.getMessage().contains("JMS Extension"));
    }

    @Test
    void destroyStopsConsumer() {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        consumer.init(dummyRouter());

        consumer.destroy();

        assertTrue(stub.stopped);
    }

    @Test
    void reInitStopsPreviousConsumer() {
        final var first = new StubConsumer();
        final var second = new StubConsumer();
        final var consumer = new JmsConsumerProxy() {
            private boolean firstCall = true;

            @Override
            JmsConsumerExtension createConsumer() {
                if (!firstCall)
                    return second;
                firstCall = false;
                return first;
            }
        };
        consumer.setConnection(connection());
        consumer.setQueue("orders");
        consumer.getTarget().setUrl("http://localhost:2000/ok");

        consumer.init(dummyRouter());
        consumer.init(dummyRouter());

        assertTrue(first.stopped);
        assertFalse(second.stopped);
    }
}
