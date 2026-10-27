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
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.ExtensionLoader.load;
import static org.apache.commons.lang3.exception.ExceptionUtils.throwableOfType;

/**
 * @description <p>Sends the body of the request as a message to a JMS queue or topic.</p>
 *
 * <p>Any broker with a Jakarta Messaging (JMS 3) client can be used.</p>
 *
 * <p>The interceptor needs the Membrane JMS Extension. Without the extension Membrane does not start.
 * Contact predic8 for the extension.</p>
 * @yaml
 * <pre><code>
 * jmsProducer:
 *   jmsConnection:
 *     connectionFactory: org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
 *     brokerURL: tcp://mq.example.com:61616
 *   queue: orders
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "jmsProducer")
public class JmsProducerInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JmsProducerInterceptor.class);

    private static final String PRODUCER_CLASS = "io.membrane_api.ext.jms.JmsProducerComponentImpl";

    /**
     * Prefix of the keys passed to the extension for the properties of the connection factory.
     */
    static final String FACTORY_PROPERTY_PREFIX = "factory.";

    public enum DeliveryMode {PERSISTENT, NON_PERSISTENT}

    private JmsConnection connection;
    private String queue;
    private String topic;
    private DeliveryMode deliveryMode = DeliveryMode.PERSISTENT;

    /**
     * Written in init(), read-only afterwards.
     */
    private JmsProducerExtension producer;

    @Override
    public void init() {
        super.init();
        // Not @Required: a connection set through $ref does not count for @Required (#3597).
        if (connection == null)
            throw new ConfigurationException("jmsProducer needs a jmsConnection.");
        if ((queue == null) == (topic == null))
            throw new ConfigurationException("jmsProducer needs either a queue or a topic.");
        // init() can run again, e.g. when the API is re-initialized
        destroy();
        producer = createProducer();
        producer.init(producerProperties());
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
        return props;
    }

    private static void putIfSet(final Map<String, String> props, final String name, final String value) {
        if (value != null)
            props.put(name, value);
    }

    @Override
    public Outcome handleRequest(final Exchange exc) {
        try {
            producer.send(exc);
            return CONTINUE;
        } catch (Exception e) {
            // An unreadable body (e.g. invalid Content-Encoding) is answered by FlowController
            final var bodyFailure = throwableOfType(e, ReadingBodyException.class);
            if (bodyFailure != null)
                throw bodyFailure;
            log.error("Could not send message to JMS destination {}.", topic != null ? topic : queue, e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not send message to JMS.")
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
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
}
