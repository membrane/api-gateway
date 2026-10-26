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
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.proxies.InternalProxy;
import com.predic8.membrane.core.proxies.Target;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static com.predic8.membrane.core.exchange.ExchangeState.COMPLETED;
import static com.predic8.membrane.core.exchange.ExchangeState.FAILED;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.AckMode.AUTO;
import static com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.AckMode.CLIENT;
import static com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.Result.*;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.junit.jupiter.api.Assertions.*;

class KafkaConsumerProxyTest {

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
     * Stands in for the Membrane Kafka Extension and keeps the handler, so a test can deliver a message.
     */
    private static class StubConsumer implements KafkaConsumerComponentExtension {
        MessageHandler handler;
        Map<String, String> properties;
        int maxRetries;
        AckMode ackMode;
        boolean stopped;

        @Override
        public void init(final Router router, final Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public void start(final String topic, final int maxRetries, final AckMode ackMode, final MessageHandler handler) {
            this.maxRetries = maxRetries;
            this.ackMode = ackMode;
            this.handler = handler;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    private static KafkaConsumerProxy consumer(final StubConsumer stub, final String targetUrl) {
        final var proxy = new KafkaConsumerProxy() {
            @Override
            KafkaConsumerComponentExtension createConsumer() {
                return stub;
            }
        };
        proxy.setBootstrapServers("localhost:9092");
        proxy.setTopic("orders");
        proxy.setGroupId("membrane");
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
        final var body = new StaticInterceptor();
        body.setSrc("x".repeat(10_000));
        api.getFlow().add(body);
        final var ret = new ReturnInterceptor();
        ret.setStatus(status);
        api.getFlow().add(ret);
        return api;
    }

    private static InternalProxy internal(final String name, final String targetUrl) {
        final var api = new InternalProxy();
        api.setName(name);
        api.setTarget(new Target());
        api.getTarget().setUrl(targetUrl);
        return api;
    }

    /**
     * Router with normal APIs on localhost:2000, /ok returns 200, /bad returns 400, /timeout returns 408,
     * /busy returns 429 and /fail returns 500, and internal APIs forwarding to them.
     */
    private void startRouter(final KafkaConsumerProxy consumer) throws Exception {
        router = new TestRouter();
        router.getConfiguration().setHotDeploy(false);
        router.add(backend("/ok", 200));
        router.add(backend("/bad", 400));
        router.add(backend("/timeout", 408));
        router.add(backend("/busy", 429));
        router.add(backend("/fail", 500));
        router.add(internal("ok-handler", "http://localhost:2000/ok"));
        router.add(internal("fail-handler", "http://localhost:2000/fail"));
        router.add(consumer);
        router.start();
    }

    /**
     * A message as the extension creates it, see {@link KafkaConsumerComponentExtension.MessageHandler}.
     */
    private static Exchange message() throws Exception {
        return Request.post("/").body("order").buildExchange();
    }

    /**
     * kafkaConsumer needs hot deployment off, which is on by default.
     */
    private static DummyTestRouter dummyRouter() {
        final var router = new DummyTestRouter();
        router.getConfiguration().setHotDeploy(false);
        return router;
    }

    @Test
    void deliversMessageThroughFlowToTarget() throws Exception {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        consumer.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(final Exchange exc) {
                exc.getRequest().getHeader().setValue("X-Source", "kafka");
                return CONTINUE;
            }
        });
        startRouter(consumer);

        final var exc = message();
        assertEquals(ACK, stub.handler.handle(exc));

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("/ok", received.getUri());
        assertEquals("kafka", received.getHeader().getFirstValue("X-Source"));
        assertEquals("order", receivedBody);
    }

    @Test
    void serverErrorOfTargetRetriesMessage() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2000/fail"));

        assertEquals(RETRY, stub.handler.handle(message()));
    }

    /**
     * Sending the same message again does not help when the target rejects it as invalid.
     */
    @Test
    void clientErrorOfTargetRejectsMessage() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2000/bad"));

        assertEquals(REJECT, stub.handler.handle(message()));
    }

    /**
     * Too Many Requests and Request Timeout are client errors, but they go away, so the message is not skipped.
     */
    @Test
    void tooManyRequestsRetriesMessage() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2000/busy"));

        assertEquals(RETRY, stub.handler.handle(message()));
    }

    @Test
    void requestTimeoutRetriesMessage() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2000/timeout"));

        assertEquals(RETRY, stub.handler.handle(message()));
    }

    @Test
    void unreachableTargetRetriesMessage() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "http://localhost:2001/down"));

        assertEquals(RETRY, stub.handler.handle(message()));
    }

    @Test
    void clientAckIsDefault() {
        final var stub = new StubConsumer();
        consumer(stub, "http://localhost:2000/ok").init(dummyRouter());

        assertEquals(CLIENT, stub.ackMode);
        assertEquals("false", stub.properties.get("enable.auto.commit"));
    }

    @Test
    void autoAckTurnsOnAutoCommit() {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        consumer.setAckMode(AUTO);
        consumer.init(dummyRouter());

        assertEquals(AUTO, stub.ackMode);
        assertEquals("true", stub.properties.get("enable.auto.commit"));
    }

    @Test
    void retriesWithoutLimitByDefault() {
        final var stub = new StubConsumer();
        consumer(stub, "http://localhost:2000/ok").init(dummyRouter());

        assertEquals(-1, stub.maxRetries);
    }

    @Test
    void maxRetriesIsPassedToExtension() {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        consumer.setMaxRetries(5);
        consumer.init(dummyRouter());

        assertEquals(5, stub.maxRetries);
    }

    /**
     * Like the server for a request over HTTP, the API has to read the response body, so that the connection to
     * the target goes back to the pool, and finish the exchange.
     */
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
        assertEquals(RETRY, stub.handler.handle(exc));

        assertTrue(exc.getResponse().getBody().isRead());
        assertEquals(FAILED, exc.getStatus());
    }

    @Test
    void deliversMessageThroughInternalApi() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "internal://ok-handler"));

        final var exc = message();
        assertEquals(ACK, stub.handler.handle(exc));

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("/ok", received.getUri());
    }

    @Test
    void serverErrorBehindInternalApiRetriesMessage() throws Exception {
        final var stub = new StubConsumer();
        startRouter(consumer(stub, "internal://fail-handler"));

        assertEquals(RETRY, stub.handler.handle(message()));
    }

    @Test
    void destroyStopsConsumer() throws Exception {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        startRouter(consumer);

        consumer.destroy();

        assertTrue(stub.stopped);
    }

    /**
     * With retryInit the router clones an inactive API, and the clone inherits the consumer and is initialized twice.
     */
    @Test
    void initAgainStopsPreviousConsumer() {
        final var stubs = new ArrayList<StubConsumer>();
        final var consumer = new KafkaConsumerProxy() {
            @Override
            KafkaConsumerComponentExtension createConsumer() {
                final var stub = new StubConsumer();
                stubs.add(stub);
                return stub;
            }
        };
        consumer.setBootstrapServers("localhost:9092");
        consumer.setTopic("orders");
        consumer.setGroupId("membrane");
        final var router = dummyRouter();

        consumer.init(router);
        consumer.init(router);

        assertEquals(2, stubs.size());
        assertTrue(stubs.getFirst().stopped);
        assertFalse(stubs.getLast().stopped);
    }

    /**
     * With retryInit the router replaces an inactive API with a clone, but only the original instance is registered
     * for @PreDestroy. Stopping the original must stop the consumer the clone started.
     */
    @Test
    void destroyOfOriginalStopsConsumerOfClone() throws Exception {
        final var stubs = new ArrayList<StubConsumer>();
        final var consumer = new KafkaConsumerProxy() {
            @Override
            KafkaConsumerComponentExtension createConsumer() {
                final var stub = new StubConsumer();
                stubs.add(stub);
                return stub;
            }
        };
        consumer.setBootstrapServers("localhost:9092");
        consumer.setTopic("orders");
        consumer.setGroupId("membrane");
        consumer.init(dummyRouter());

        consumer.clone();
        consumer.destroy();

        assertTrue(stubs.getLast().stopped);
    }

    /**
     * Removing or replacing an API does not stop its consumer, so changing it by hot deployment would leave the old
     * consumer running.
     */
    @Test
    void hotDeployFailsOnStartup() {
        final var router = new DummyTestRouter();
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");

        final var e = assertThrows(ConfigurationException.class, () -> consumer.init(router));

        assertTrue(e.getMessage().contains("hotDeploy"), e.getMessage());
        assertNull(stub.handler);
    }

    /**
     * With retryInit an inactive API would keep its consumer running, and every retry would restart it.
     */
    @Test
    void retryInitIsRejected() {
        final var router = dummyRouter();
        router.getConfiguration().setRetryInit(true);
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");

        // With retryInit the router catches the exception and keeps the API inactive
        consumer.init(router);

        assertFalse(consumer.isActive());
        assertTrue(consumer.getErrorState().contains("retryInit"), consumer.getErrorState());
        assertNull(stub.handler);
    }

    @Test
    void parsesYamlWithFlowAndTarget(@TempDir final Path dir) throws Exception {
        final var config = dir.resolve("apis.yaml");
        Files.writeString(config, """
                configuration:
                  hotDeploy: false
                ---
                kafkaConsumer:
                  name: orders-in
                  bootstrapServers: localhost:9092
                  topic: orders
                  groupId: membrane
                  clientId: membrane-orders
                  flow:
                    - setHeader:
                        name: X-Source
                        value: kafka
                  target:
                    url: https://orders.example.com/orders
                """);

        final var router = new DefaultRouter();
        loadIntoRouter(router, config.toString());

        final var consumer = assertInstanceOf(KafkaConsumerProxy.class, router.getRuleManager().getRules().getFirst());
        assertEquals("orders-in", consumer.getName());
        assertEquals("membrane-orders", consumer.getClientId());
        assertEquals(1, consumer.getFlow().size());
        assertEquals("https://orders.example.com/orders", consumer.getTarget().getUrl());

        // Only the missing extension stops the startup
        final var e = assertThrows(Exception.class, router::init);
        assertTrue(getRootCause(e).getMessage().contains("Membrane Kafka Extension"), () -> getStackTrace(e));
    }

    @Test
    void nameDefaultsToTopicAndGroup() {
        final var consumer = consumer(new StubConsumer(), "http://localhost:2000/ok");
        consumer.init(dummyRouter());
        assertEquals("orders-membrane", consumer.getName());
    }

    @Test
    void configuredNameIsKept() {
        final var consumer = consumer(new StubConsumer(), "http://localhost:2000/ok");
        consumer.setName("orders-in");
        consumer.init(dummyRouter());
        assertEquals("orders-in", consumer.getName());
    }

    @Test
    void clientIdIsPassedToKafka() {
        final var stub = new StubConsumer();
        final var consumer = consumer(stub, "http://localhost:2000/ok");
        consumer.setClientId("membrane-orders");
        consumer.init(dummyRouter());
        assertEquals("membrane-orders", stub.properties.get("client.id"));
    }

    @Test
    void missingExtensionFailsOnStartup() {
        final var consumer = new KafkaConsumerProxy();
        consumer.setBootstrapServers("localhost:9092");
        consumer.setTopic("orders");
        consumer.setGroupId("membrane");
        final var e = assertThrows(ConfigurationException.class, () -> consumer.init(dummyRouter()));
        assertTrue(e.getMessage().contains("Membrane Kafka Extension"));
    }
}
