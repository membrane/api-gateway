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

import com.predic8.membrane.core.lang.spel.spelable.SpELMap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Header fields and properties of a JMS message received by a jmsConsumer. The exchange property
 * {@link JmsConsumerExtension#JMS_PROPERTY} holds it, so a flow reads e.g. <code>${property.jms.messageId}</code> or
 * <code>${property.jms.properties.orderId}</code>.
 * <p>
 * Fields the message does not have are <code>null</code>. Immutable; created by the JMS extension with
 * {@link #builder()}.
 */
public final class JmsMessageInfo {

    private final String messageId;
    private final String correlationId;
    private final String type;
    private final String destination;
    private final String replyTo;
    private final String deliveryMode;
    private final int priority;
    private final boolean redelivered;
    private final Integer deliveryCount;
    private final Long timestamp;
    private final Long expiration;
    private final Long deliveryTime;
    private final Map<String, Object> properties;

    private JmsMessageInfo(final Builder builder) {
        messageId = builder.messageId;
        correlationId = builder.correlationId;
        type = builder.type;
        destination = builder.destination;
        replyTo = builder.replyTo;
        deliveryMode = builder.deliveryMode;
        priority = builder.priority;
        redelivered = builder.redelivered;
        deliveryCount = builder.deliveryCount;
        timestamp = builder.timestamp;
        expiration = builder.expiration;
        deliveryTime = builder.deliveryTime;
        // SpELMap lets SpEL read the properties with dots: property.jms.properties.orderId
        properties = new SpELMap<>(Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties)));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return <code>JMSMessageID</code>
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * @return <code>JMSCorrelationID</code>
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * @return <code>JMSType</code>
     */
    public String getType() {
        return type;
    }

    /**
     * @return Name of the queue or topic the message was received from (<code>JMSDestination</code>)
     */
    public String getDestination() {
        return destination;
    }

    /**
     * @return Name of the queue or topic of <code>JMSReplyTo</code>
     */
    public String getReplyTo() {
        return replyTo;
    }

    /**
     * @return <code>PERSISTENT</code> or <code>NON_PERSISTENT</code> (<code>JMSDeliveryMode</code>)
     */
    public String getDeliveryMode() {
        return deliveryMode;
    }

    /**
     * @return 0 (lowest) to 9 (highest) (<code>JMSPriority</code>)
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @return Whether the message was delivered before (<code>JMSRedelivered</code>)
     */
    public boolean isRedelivered() {
        return redelivered;
    }

    /**
     * @return Number of deliveries including this one, starting at 1 (<code>JMSXDeliveryCount</code>)
     */
    public Integer getDeliveryCount() {
        return deliveryCount;
    }

    /**
     * @return Time the message was sent, in milliseconds since 1970 (<code>JMSTimestamp</code>)
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * @return Time the message expires, in milliseconds since 1970 (<code>JMSExpiration</code>)
     */
    public Long getExpiration() {
        return expiration;
    }

    /**
     * @return Earliest time the message is delivered, in milliseconds since 1970 (<code>JMSDeliveryTime</code>)
     */
    public Long getDeliveryTime() {
        return deliveryTime;
    }

    /**
     * @return JMS properties of the message with their types, e.g. <code>Integer</code> or <code>String</code>.
     * Read-only.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "JmsMessageInfo{messageId=%s, correlationId=%s, type=%s, destination=%s, replyTo=%s, deliveryMode=%s, priority=%d, redelivered=%s, deliveryCount=%s, timestamp=%s, expiration=%s, deliveryTime=%s, properties=%s}"
                .formatted(messageId, correlationId, type, destination, replyTo, deliveryMode, priority, redelivered, deliveryCount, timestamp, expiration, deliveryTime, properties.keySet());
    }

    public static final class Builder {
        private String messageId;
        private String correlationId;
        private String type;
        private String destination;
        private String replyTo;
        private String deliveryMode;
        private int priority = 4;
        private boolean redelivered;
        private Integer deliveryCount;
        private Long timestamp;
        private Long expiration;
        private Long deliveryTime;
        private final Map<String, Object> properties = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder messageId(final String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder correlationId(final String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Builder destination(final String destination) {
            this.destination = destination;
            return this;
        }

        public Builder replyTo(final String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        public Builder deliveryMode(final String deliveryMode) {
            this.deliveryMode = deliveryMode;
            return this;
        }

        public Builder priority(final int priority) {
            this.priority = priority;
            return this;
        }

        public Builder redelivered(final boolean redelivered) {
            this.redelivered = redelivered;
            return this;
        }

        public Builder deliveryCount(final Integer deliveryCount) {
            this.deliveryCount = deliveryCount;
            return this;
        }

        public Builder timestamp(final Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder expiration(final Long expiration) {
            this.expiration = expiration;
            return this;
        }

        public Builder deliveryTime(final Long deliveryTime) {
            this.deliveryTime = deliveryTime;
            return this;
        }

        public Builder property(final String name, final Object value) {
            properties.put(name, value);
            return this;
        }

        public JmsMessageInfo build() {
            return new JmsMessageInfo(this);
        }
    }
}
