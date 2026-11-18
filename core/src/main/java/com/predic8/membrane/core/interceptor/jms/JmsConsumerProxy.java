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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.jms.JmsConsumerExtension.Result;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;
import com.predic8.membrane.core.proxies.InternalProxyKey;
import com.predic8.membrane.core.proxies.NotPortOpeningProxy;
import com.predic8.membrane.core.util.ConfigurationException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.RuleMatchingInterceptor.assignRule;
import static com.predic8.membrane.core.interceptor.jms.JmsConsumerExtension.Result.ACK;
import static com.predic8.membrane.core.interceptor.jms.JmsConsumerExtension.Result.ROLLBACK;
import static com.predic8.membrane.core.interceptor.jms.JmsProducerInterceptor.FACTORY_PROPERTY_PREFIX;
import static com.predic8.membrane.core.util.ExtensionLoader.load;

/**
 * @description <p>Consumes messages from a JMS queue or topic. Every message runs through the flow of this API and
 * is sent to its target, like a request that came in over HTTP. The target can be an HTTP backend or another API
 * using <code>internal://</code>.</p>
 *
 * <p>A message is acknowledged when the target answers with a status below 400. Otherwise, e.g. after a server
 * error or an unreachable target, the message is not acknowledged and the broker delivers it again according to its
 * redelivery policy, and finally moves it to its dead-letter queue. A message can be delivered more than once, so the
 * target should handle duplicates, e.g. by the message id passed in a header.</p>
 *
 * <p>A redirect (3xx) counts as delivered and the message is acknowledged, but Membrane does not follow it, so the
 * message does not reach the new location. Use the final URL of the backend as the target.</p>
 *
 * <p>The request carries the message body and its Content-Type. The header fields and properties of the message are
 * not passed to the target. The flow reads them from the exchange property <code>jms</code>, e.g.
 * <code>${property.jms.messageId}</code>, <code>${property.jms.correlationId}</code>,
 * <code>${property.jms.deliveryCount}</code> or <code>${property.jms.properties.orderId}</code>, and can pass them on
 * as headers.</p>
 *
 * <p>Any broker with a Jakarta Messaging (JMS 3) client can be used.</p>
 *
 * <p>The API does not support retryInit, which is off by default.</p>
 *
 * <p>The API needs the Membrane JMS Extension. Without the extension Membrane does not start.
 * Contact predic8 for the extension.</p>
 * @yaml
 * <pre><code>
 * jmsConsumer:
 *   name: orders-in
 *   jmsConnection:
 *     connectionFactory: org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
 *     brokerURL: tcp://localhost:61616
 *   queue: orders
 *   flow:
 *     - setHeader:
 *         name: Idempotency-Key
 *         value: ${property.jms.messageId}
 *   target:
 *     url: https://orders.example.com/orders
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "jmsConsumer", topLevel = true, component = false)
public class JmsConsumerProxy extends AbstractServiceProxy implements NotPortOpeningProxy {

    private static final Logger log = LoggerFactory.getLogger(JmsConsumerProxy.class);

    private static final String CONSUMER_CLASS = "io.membrane_api.ext.jms.JmsConsumerComponentImpl";

    private JmsConnection connection;
    private String queue;
    private String topic;
    private String subscription;
    private int concurrency = 1;

    /**
     * Shared with clones, since clone() copies the reference: with retryInit the router replaces this instance with
     * a clone, but only this instance is registered for @PreDestroy, so destroy() has to reach the consumer the
     * clone started.
     */
    private final AtomicReference<JmsConsumerExtension> consumer = new AtomicReference<>();

    public JmsConsumerProxy() {
        key = new InternalProxyKey();
    }

    /**
     * Called before the interceptors of the flow are initialized. The extension waits until the router is running
     * before it hands over messages, see {@link JmsConsumerExtension#start(JmsConsumerExtension.MessageHandler)}.
     * <p>
     * Can run more than once. The running consumer is stopped first, so only one consumer keeps running.
     */
    @Override
    public void init() {
        super.init();
        // Not @Required: a connection set through $ref does not count for @Required (#3597).
        if (connection == null)
            throw new ConfigurationException("jmsConsumer needs a jmsConnection.");
        if ((queue == null) == (topic == null))
            throw new ConfigurationException("jmsConsumer needs either a queue or a topic.");
        if (subscription != null && topic == null)
            throw new ConfigurationException("subscription of jmsConsumer can only be used with a topic.");
        if (concurrency < 1)
            throw new ConfigurationException("concurrency of jmsConsumer must be at least 1.");
        // Without a shared subscription every session would receive its own copy of every message
        if (topic != null && subscription == null && concurrency > 1)
            throw new ConfigurationException("jmsConsumer needs a subscription to read a topic with a concurrency above 1.");
        if (name == null || name.isEmpty())
            name = defaultName(topic != null ? topic : queue);
        ((InternalProxyKey) key).setServiceName(getName());
        checkRouterConfiguration();
        stopConsumer();
        final var created = createConsumer();
        checkApiVersion(created);
        consumer.set(created);
        created.init(router, consumerProperties());
        created.start(this::handle);
    }

    /**
     * Not the bare destination name: the name is also what <code>internal://name</code> looks up, so a consumer
     * named <code>orders</code> would hide an internal API <code>orders</code> and could route messages to itself.
     *
     * @param destination Name of the queue or topic
     * @return Name of a jmsConsumer that has none configured
     */
    public static String defaultName(final String destination) {
        return "JMSConsumer: " + destination;
    }

    /**
     * Package-private so that tests can replace the extension with a stub.
     */
    JmsConsumerExtension createConsumer() {
        return load(CONSUMER_CLASS, JmsConsumerExtension.class,
                "The Membrane JMS Extension is needed for the jmsConsumer. Ask info@predic8.de.");
    }

    private static void checkApiVersion(final JmsConsumerExtension extension) {
        int version;
        try {
            version = extension.apiVersion();
        } catch (AbstractMethodError e) {
            // Extension built before apiVersion() existed
            version = 0;
        }
        if (version != JmsConsumerExtension.API_VERSION)
            throw new ConfigurationException(
                    "The Membrane JMS Extension has API version %d, but jmsConsumer needs version %d. Use a matching version of the extension."
                            .formatted(version, JmsConsumerExtension.API_VERSION));
    }

    /**
     * retryInit would leave a consumer running: the consumer starts before the interceptors of the flow are
     * initialized. If one of them fails, the API stays inactive with a running consumer that does not deliver, and
     * every retry restarts it.
     * <p>
     * Hot deployment of YAML is supported: a reload closes the bean registry, which calls {@link #destroy()} of the
     * old API before the new one starts. Hot deployment of XML is not, since Spring does not call @PreDestroy.
     */
    private void checkRouterConfiguration() {
        final var configuration = router.getConfiguration();
        if (configuration.isRetryInit())
            throw new ConfigurationException("jmsConsumer %s does not support retryInit: while the API is inactive its consumer would keep reading without delivering. Set retryInit to false.".formatted(getName()));
    }

    @PreDestroy
    public void destroy() {
        stopConsumer();
    }

    /**
     * Called when the router stops, before the HTTP clients are closed.
     */
    @Override
    public void stopInbound() {
        stopConsumer();
    }

    private void stopConsumer() {
        final var running = consumer.getAndSet(null);
        if (running != null)
            running.stop();
    }

    private Map<String, String> consumerProperties() {
        final var props = new HashMap<String, String>();
        props.put("connection.factory", connection.getConnectionFactory());
        putIfSet(props, "username", connection.getUsername());
        putIfSet(props, "password", connection.getPassword());
        connection.getFactoryProperties().forEach((name, value) -> props.put(FACTORY_PROPERTY_PREFIX + name, value));
        putIfSet(props, "queue", queue);
        putIfSet(props, "topic", topic);
        putIfSet(props, "subscription", subscription);
        props.put("concurrency", String.valueOf(concurrency));
        return props;
    }

    private static void putIfSet(final Map<String, String> props, final String name, final String value) {
        if (value != null)
            props.put(name, value);
    }

    /**
     * Runs a received message through the same flow as a request that came in over HTTP. The API is assigned
     * beforehand, so the flow skips matching a request to an API and continues with the flow and the target of
     * this API.
     * <p>
     * Afterwards the response body is read, like the server does when it writes the response to the client, so the
     * connection to the target goes back to the pool.
     */
    Result handle(final Exchange exc) {
        final var destination = topic != null ? topic : queue;
        if (!isActive()) {
            log.warn("jmsConsumer {} is not active. Message from {} will be delivered again.", getName(), destination);
            return ROLLBACK;
        }
        try {
            assignRule(exc, this);
            final var outcome = router.getFlowController().invokeRequestHandlers(exc, router.getTransport().getFlow());
            final var response = exc.getResponse();
            // The status decides; a truncated body after a 2xx doesn't undo the backend's success.
            if (response != null)
                response.discardBody();
            if (response != null && response.getStatusCode() < 400 && outcome != ABORT) {
                exc.setCompleted();
                return ACK;
            }
            final var status = response == null ? "no response" : "status " + response.getStatusCode();
            exc.finishExchange(true, status);
            log.info("Delivering message from {} by jmsConsumer {} failed with {}{}. The broker delivers it again.", destination, getName(), status, deliveryCount(exc));
            return ROLLBACK;
        } catch (Exception e) {
            log.warn("Error delivering message from {} by jmsConsumer {}. The broker delivers it again.", destination, getName(), e);
            exc.finishExchange(true, e.getMessage());
            return ROLLBACK;
        }
    }

    /**
     * Shows how close a message is to the broker's limit of deliveries, after which it goes to the dead-letter queue.
     *
     * @return e.g. <code> (delivery 3)</code>, or an empty string if the broker does not report the count
     */
    private static String deliveryCount(final Exchange exc) {
        if (exc.getProperty(JmsConsumerExtension.JMS_PROPERTY) instanceof JmsMessageInfo info && info.getDeliveryCount() != null)
            return " (delivery %d)".formatted(info.getDeliveryCount());
        return "";
    }

    /**
     * @description Connection to the JMS broker, inline or referenced with <code>$ref</code>. Required.
     */
    @MCChildElement(order = 0)
    public void setConnection(JmsConnection connection) {
        this.connection = connection;
    }

    public JmsConnection getConnection() {
        return connection;
    }

    /**
     * @description Queue the messages are read from. Set either queue or topic.
     * @example orders
     */
    @MCAttribute
    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getQueue() {
        return queue;
    }

    /**
     * @description Topic the messages are read from. Set either queue or topic.
     * @example order-events
     */
    @MCAttribute
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * @description Name of a durable shared subscription on the topic. Membrane instances with the same subscription
     * share the messages of the topic, and messages published while Membrane is down are kept by the broker. Without
     * a subscription every instance receives every message, but only while it is running. Only for a topic.
     * @example membrane
     */
    @MCAttribute
    public void setSubscription(String subscription) {
        this.subscription = subscription;
    }

    public String getSubscription() {
        return subscription;
    }

    /**
     * @description Number of messages that are processed in parallel. With 1 the messages are processed one after
     * the other. Above 1, a topic needs a subscription.
     * @default 1
     * @example 4
     */
    @MCAttribute
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getConcurrency() {
        return concurrency;
    }
}
