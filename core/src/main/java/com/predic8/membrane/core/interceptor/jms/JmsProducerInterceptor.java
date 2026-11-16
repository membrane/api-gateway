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
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractLanguageInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import com.predic8.membrane.core.lang.TemplateExchangeExpression;
import com.predic8.membrane.core.util.ConfigurationException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.ExtensionLoader.load;
import static org.apache.commons.lang3.exception.ExceptionUtils.throwableOfType;

/**
 * @description <p>Sends the body of the request or the response as a message to a JMS queue or topic.</p>
 *
 * <p>In the flow of an API, the interceptor sends a message for the request and another one for the response.
 * Put it into <code>request</code> or <code>response</code> to send only one of them.</p>
 *
 * <p>Header fields and properties of the message can be set with expressions, e.g. from the request or from the
 * message a jmsConsumer received (<code>${property.jms.correlationId}</code>). After sending, the exchange property
 * <code>jmsSent</code> describes the sent message, e.g. <code>${property.jmsSent.messageId}</code>. When the
 * interceptor sends in the request and the response flow, it holds the message sent last.</p>
 *
 * <p>Any broker with a Jakarta Messaging (JMS 3) client can be used.</p>
 *
 * <p>The interceptor needs the Membrane JMS Extension. Without the extension Membrane does not start.
 * Contact predic8 for the extension.</p>
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - request:
 *         - jmsProducer:
 *             jmsConnection:
 *               connectionFactory: org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
 *               brokerURL: tcp://mq.example.com:61616
 *             queue: orders
 *             correlationId: ${header['X-Request-Id']}
 *             priority: 6
 *             properties:
 *               orderId: ${json.id}
 *     - template:
 *         contentType: application/json
 *         src: '{"messageId": "${property.jmsSent.messageId}"}'
 *     - return:
 *         status: 202
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "jmsProducer")
public class JmsProducerInterceptor extends AbstractLanguageInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JmsProducerInterceptor.class);

    private static final String PRODUCER_CLASS = "io.membrane_api.ext.jms.JmsProducerComponentImpl";

    /**
     * Prefix of the keys passed to the extension for the properties of the connection factory.
     */
    static final String FACTORY_PROPERTY_PREFIX = "factory.";

    /**
     * Name of the exchange property holding the {@link JmsMessageInfo} of the message sent last.
     */
    public static final String JMS_SENT_PROPERTY = "jmsSent";

    /**
     * Words of the JMS message selector syntax, which cannot be property names.
     */
    private static final Set<String> SELECTOR_KEYWORDS = Set.of("NULL", "TRUE", "FALSE", "NOT", "AND", "OR", "BETWEEN", "LIKE", "IN", "IS", "ESCAPE");

    private static final String TOPIC_PREFIX = "topic:";
    private static final String QUEUE_PREFIX = "queue:";

    public enum DeliveryMode {PERSISTENT, NON_PERSISTENT}

    private JmsConnection connection;
    private String queue;
    private String topic;
    private DeliveryMode deliveryMode = DeliveryMode.PERSISTENT;
    private int sendTimeout = 10000;
    private int priority = 4;
    private long timeToLive;
    private long deliveryDelay;
    private String correlationId;
    private String type;
    private String replyTo;
    private JmsProducerProperties properties;

    // Written in init(), read-only afterwards
    private JmsProducerExtension producer;
    private ExchangeExpression correlationIdExpression;
    private ExchangeExpression typeExpression;
    private ExchangeExpression replyToExpression;
    private Map<String, ExchangeExpression> propertyExpressions;

    @Override
    public void init() {
        super.init();
        // Not @Required: a connection set through $ref does not count for @Required (#3597).
        if (connection == null)
            throw new ConfigurationException("jmsProducer needs a jmsConnection.");
        if ((queue == null) == (topic == null))
            throw new ConfigurationException("jmsProducer needs either a queue or a topic.");
        if (sendTimeout < 0)
            throw new ConfigurationException("sendTimeout of jmsProducer must not be negative.");
        if (priority < 0 || priority > 9)
            throw new ConfigurationException("priority of jmsProducer must be between 0 and 9.");
        if (timeToLive < 0)
            throw new ConfigurationException("timeToLive of jmsProducer must not be negative.");
        if (deliveryDelay < 0)
            throw new ConfigurationException("deliveryDelay of jmsProducer must not be negative.");
        compileExpressions();
        // init() can run again, e.g. when the API is re-initialized
        destroy();
        final var created = createProducer();
        checkApiVersion(created);
        producer = created;
        producer.init(producerProperties());
    }

    private void compileExpressions() {
        correlationIdExpression = compile(correlationId);
        typeExpression = compile(type);
        replyToExpression = compile(replyTo);
        final var compiled = new LinkedHashMap<String, ExchangeExpression>();
        if (properties != null) {
            properties.getProperties().forEach((name, value) -> {
                checkPropertyName(name);
                compiled.put(name, compile(value));
            });
        }
        propertyExpressions = compiled;
    }

    private ExchangeExpression compile(final String value) {
        return value == null ? null : TemplateExchangeExpression.newInstance(this, language, value);
    }

    /**
     * JMS property names follow the rules for identifiers of the message selector syntax.
     */
    static void checkPropertyName(final String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0)) || !name.chars().skip(1).allMatch(Character::isJavaIdentifierPart))
            throw new ConfigurationException("JMS property name '%s' of jmsProducer must be a Java identifier, e.g. orderId.".formatted(name));
        if (name.startsWith("JMS"))
            throw new ConfigurationException("JMS property name '%s' of jmsProducer must not start with JMS, which is reserved for JMS.".formatted(name));
        if (SELECTOR_KEYWORDS.contains(name.toUpperCase(Locale.ROOT)))
            throw new ConfigurationException("JMS property name '%s' of jmsProducer is a word of the JMS selector syntax.".formatted(name));
        if (name.equals("contentType"))
            throw new ConfigurationException("JMS property contentType is set by jmsProducer from the Content-Type of the message.");
    }

    private static void checkApiVersion(final JmsProducerExtension extension) {
        int version;
        try {
            version = extension.apiVersion();
        } catch (AbstractMethodError e) {
            // Extension built before apiVersion() existed
            version = 0;
        }
        if (version != JmsProducerExtension.API_VERSION)
            throw new ConfigurationException(
                    "The Membrane JMS Extension has API version %d, but jmsProducer needs version %d. Use a matching version of the extension."
                            .formatted(version, JmsProducerExtension.API_VERSION));
    }

    /**
     * Package-private so that tests can replace the extension with a stub.
     */
    JmsProducerExtension createProducer() {
        return load(PRODUCER_CLASS, JmsProducerExtension.class,
                "The Membrane JMS Extension is needed for the jmsProducer interceptor. Ask info@predic8.de.");
    }

    @PreDestroy
    public void destroy() {
        if (producer == null)
            return;
        producer.stop();
        producer = null;
    }

    private Map<String, String> producerProperties() {
        final var props = new HashMap<String, String>();
        props.put("connection.factory", connection.getConnectionFactory());
        putIfSet(props, "username", connection.getUsername());
        putIfSet(props, "password", connection.getPassword());
        connection.getFactoryProperties().forEach((name, value) -> props.put(FACTORY_PROPERTY_PREFIX + name, value));
        putIfSet(props, "queue", queue);
        putIfSet(props, "topic", topic);
        props.put("delivery.mode", deliveryMode.name());
        props.put("send.timeout", String.valueOf(sendTimeout));
        props.put("priority", String.valueOf(priority));
        props.put("time.to.live", String.valueOf(timeToLive));
        props.put("delivery.delay", String.valueOf(deliveryDelay));
        return props;
    }

    private static void putIfSet(final Map<String, String> props, final String name, final String value) {
        if (value != null)
            props.put(name, value);
    }

    @Override
    public Outcome handleRequest(final Exchange exc) {
        return send(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(final Exchange exc) {
        return send(exc, RESPONSE);
    }

    private Outcome send(final Exchange exc, final Flow flow) {
        final JmsOutgoingMessage message;
        try {
            message = outgoingMessage(exc, flow);
        } catch (ExchangeExpressionException e) {
            // FlowController answers an unreadable body like any other plugin's (400 body-decoding)
            final var bodyFailure = throwableOfType(e, ReadingBodyException.class);
            if (bodyFailure != null)
                throw bodyFailure;
            final var pd = e.emptyProblemDetails(router.getConfiguration().isProduction(), getDisplayName(), flow);
            e.provideDetails(pd);
            pd.title("Error evaluating an expression for the JMS message").buildAndSetResponse(exc);
            return ABORT;
        }
        try {
            exc.setProperty(JMS_SENT_PROPERTY, producer.send(exc, flow, message));
            return CONTINUE;
        } catch (Exception e) {
            // An unreadable body (e.g. invalid Content-Encoding) is answered by FlowController
            final var bodyFailure = throwableOfType(e, ReadingBodyException.class);
            if (bodyFailure != null)
                throw bodyFailure;
            logSendFailure(e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not send message to JMS.")
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private JmsOutgoingMessage outgoingMessage(final Exchange exc, final Flow flow) {
        final var values = new LinkedHashMap<String, Object>();
        propertyExpressions.forEach((name, expression) -> {
            final var value = toJmsValue(expression.evaluate(exc, flow, Object.class));
            if (value != null)
                values.put(name, value);
        });
        final var replyToValue = evaluate(replyToExpression, exc, flow);
        String replyToQueue = null;
        String replyToTopic = null;
        if (replyToValue != null && replyToValue.startsWith(TOPIC_PREFIX))
            replyToTopic = replyToValue.substring(TOPIC_PREFIX.length());
        else if (replyToValue != null && replyToValue.startsWith(QUEUE_PREFIX))
            replyToQueue = replyToValue.substring(QUEUE_PREFIX.length());
        else
            replyToQueue = replyToValue;
        return new JmsOutgoingMessage(evaluate(correlationIdExpression, exc, flow), evaluate(typeExpression, exc, flow), replyToQueue, replyToTopic, values);
    }

    private static String evaluate(final ExchangeExpression expression, final Exchange exc, final Flow flow) {
        if (expression == null)
            return null;
        // Not String.class: that turns a missing value into the text "null"
        final var value = expression.evaluate(exc, flow, Object.class);
        return value == null || value.toString().isEmpty() ? null : value.toString();
    }

    /**
     * JMS properties can only hold strings, booleans and numbers of the primitive types. Other numbers are
     * converted, everything else is sent as text.
     */
    static Object toJmsValue(final Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case Boolean b -> b;
            case Byte b -> b;
            case Short s -> s;
            case Integer i -> i;
            case Long l -> l;
            case Float f -> f;
            case Double d -> d;
            case BigInteger i when i.bitLength() < 64 -> i.longValue();
            case BigDecimal d -> d.doubleValue();
            default -> value.toString();
        };
    }

    /**
     * An IOException is the failure the extension reports, e.g. a broker that is down. Its message tells what
     * happened, a stack trace does not help. Other exceptions are bugs and are logged with their stack trace.
     */
    private void logSendFailure(final Exception e) {
        final var destination = topic != null ? topic : queue;
        if (!(e instanceof IOException)) {
            log.error("Could not send message to JMS destination {}.", destination, e);
            return;
        }
        if (e.getCause() != null)
            log.error("Could not send message to JMS destination {}: {} Reason: {}", destination, e.getMessage(), e.getCause().getMessage());
        else
            log.error("Could not send message to JMS destination {}: {}", destination, e.getMessage());
        log.debug("Stack trace of the JMS failure:", e);
    }

    @Override
    public String getDisplayName() {
        return "JMS Producer";
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
     * @description Queue the messages are sent to. Set either queue or topic.
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
     * @description Topic the messages are published to. Set either queue or topic.
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
     * @description Whether the broker stores the messages so that they survive a restart of the broker.
     * @default persistent
     * @example persistent | non_persistent
     */
    @MCAttribute
    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    /**
     * @description Time in milliseconds the broker may take to accept a message. If it takes longer, e.g. because
     * the broker blocks producers when its disk is full, the request is answered with 500. 0 waits without limit.
     * @default 10000
     * @example 30000
     */
    @MCAttribute
    public void setSendTimeout(int sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public int getSendTimeout() {
        return sendTimeout;
    }

    /**
     * @description Priority of the messages, from 0 (lowest) to 9 (highest). Brokers may deliver messages with a
     * higher priority first.
     * @default 4
     * @example 6
     */
    @MCAttribute
    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * @description Time in milliseconds after which the broker discards a message that was not consumed. 0 keeps
     * the message until it is consumed.
     * @default 0
     * @example 60000
     */
    @MCAttribute
    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
    }

    public long getTimeToLive() {
        return timeToLive;
    }

    /**
     * @description Time in milliseconds the broker waits before it delivers a message to consumers.
     * @default 0
     * @example 5000
     */
    @MCAttribute
    public void setDeliveryDelay(long deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
    }

    public long getDeliveryDelay() {
        return deliveryDelay;
    }

    /**
     * @description Correlation id of the messages (<code>JMSCorrelationID</code>), e.g. to relate a reply to its
     * request. Can contain expressions.
     * @example ${header['X-Request-Id']}
     */
    @MCAttribute
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * @description Type of the messages (<code>JMSType</code>). Can contain expressions.
     * @example order-created
     */
    @MCAttribute
    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    /**
     * @description Queue a receiver should send its reply to (<code>JMSReplyTo</code>). For a topic, prefix the
     * name with <code>topic:</code>. Can contain expressions. Membrane does not wait for the reply.
     * @example replies | topic:order-replies
     */
    @MCAttribute
    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getReplyTo() {
        return replyTo;
    }

    /**
     * @description JMS properties of the messages.
     */
    @MCChildElement(order = 1)
    public void setProperties(JmsProducerProperties properties) {
        this.properties = properties;
    }

    public JmsProducerProperties getProperties() {
        return properties;
    }
}
