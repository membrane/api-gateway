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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.InternalRoutingInterceptor;
import com.predic8.membrane.core.util.ConfigurationException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.kafka.KafkaExtension.*;

/**
 * Open point (developer note, not part of the reference documentation):
 * <ul>
 * <li>destroy() is a PreDestroy method of the bean registry. Whether the registry calls it for an interceptor
 * nested in the flow of an API when the router stops is not confirmed. Tests only call destroy() directly.</li>
 * </ul>
 *
 * @description <p>Consumes messages from an Apache Kafka topic and forwards each message to an internal API.</p>
 *
 * <p>The interceptor needs the Membrane Kafka Extension. Without the extension no messages are received,
 * calls are only logged. Contact predic8 for the extension.</p>
 * @yaml
 * <pre><code>
 * kafkaConsumer:
 *   bootstrapServers: kafka.example.com:9092
 *   topic: orders
 *   groupId: membrane
 *   forward: internal://order-handler
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "kafkaConsumer", component = true)
public class KafkaConsumerInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerInterceptor.class);

    private String bootstrapServers;
    private String topic;
    private String forward;
    private String groupId;
    private String clientId;
    private String autoOffsetReset = "latest";
    private boolean enableAutoCommit = true;
    private int maxPollRecords = 500;
    private int sessionTimeoutMs = 45000;
    private int maxPollIntervalMs = 300000;
    private int fetchMinBytes = 1;
    private int fetchMaxWaitMs = 500;
    private String securityProtocol;
    private String saslMechanism;
    private String saslJaasConfig;

    /**
     * Written in init(), read-only afterwards.
     */
    private KafkaConsumerComponentExtension consumer;
    private InternalRoutingInterceptor internalRouting;

    @Override
    public void init() {
        super.init();
        if (forward == null || !forward.startsWith("internal:"))
            throw new ConfigurationException("The forward of kafkaConsumer must be the URI of an internal API, e.g. internal://order-handler, but was '%s'.".formatted(forward));
        if (!isOnClasspath(CONSUMER_CLASS))
            log.warn("The Membrane Kafka Extension is needed for the kafkaConsumer interceptor. Without it no messages are received from Kafka. Ask info@predic8.de.");
        internalRouting = new InternalRoutingInterceptor();
        internalRouting.init(router);
        consumer = load(CONSUMER_CLASS, KafkaConsumerComponentExtension.class, DummyKafkaConsumerComponentExtension::new);
        consumer.init(router, consumerProperties());
        consumer.start(topic, this::forward);
    }

    /**
     * Messages can only be forwarded after all APIs, including the internal one, are initialized.
     */
    @PreDestroy
    public void destroy() {
        if (consumer != null)
            consumer.stop();
    }

    private Map<String, String> consumerProperties() {
        final var props = new HashMap<String, String>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("auto.offset.reset", autoOffsetReset);
        props.put("enable.auto.commit", String.valueOf(enableAutoCommit));
        props.put("max.poll.records", String.valueOf(maxPollRecords));
        props.put("session.timeout.ms", String.valueOf(sessionTimeoutMs));
        props.put("max.poll.interval.ms", String.valueOf(maxPollIntervalMs));
        props.put("fetch.min.bytes", String.valueOf(fetchMinBytes));
        props.put("fetch.max.wait.ms", String.valueOf(fetchMaxWaitMs));
        putIfSet(props, "group.id", groupId);
        putIfSet(props, "client.id", clientId);
        putIfSet(props, "security.protocol", securityProtocol);
        putIfSet(props, "sasl.mechanism", saslMechanism);
        putIfSet(props, "sasl.jaas.config", saslJaasConfig);
        return props;
    }

    private static void putIfSet(final Map<String, String> props, final String name, final String value) {
        if (value != null)
            props.put(name, value);
    }

    /**
     * Sends a received message through the internal API named by forward.
     */
    void forward(final Exchange exc) throws Exception {
        exc.getDestinations().clear();
        exc.getDestinations().add(forward);
        final var outcome = internalRouting.handleRequest(exc);
        final var response = exc.getResponse();
        if (outcome == ABORT || (response != null && response.getStatusCode() >= 400))
            throw new IllegalStateException("Forwarding Kafka message to %s failed: %s".formatted(forward,
                    response == null ? "aborted" : "status " + response.getStatusCode()));
    }

    @Override
    public String getDisplayName() {
        return "Kafka Consumer";
    }

    /**
     * @description Comma separated list of Kafka brokers used for the initial connection.
     * @example kafka1.example.com:9092,kafka2.example.com:9092
     */
    @MCAttribute
    @Required
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * @description Topic the messages are read from.
     * @example orders
     */
    @MCAttribute
    @Required
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * @description URI of an internal API that receives every message.
     * The record key, if present, is available to the internal API in the exchange property membrane.ext.kafka.key.
     * @example internal://order-handler
     */
    @MCAttribute
    @Required
    public void setForward(String forward) {
        this.forward = forward;
    }

    public String getForward() {
        return forward;
    }

    /**
     * @description Consumer group the interceptor joins. Consumers of the same group share the partitions of the topic.
     * @example membrane
     */
    @MCAttribute
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * @description Client id sent to the brokers, useful for logging and quotas on the Kafka side.
     * @example membrane
     */
    @MCAttribute
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * @description Where to start reading when the group has no committed offset.
     * @default latest
     * @example earliest | latest
     */
    @MCAttribute
    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    /**
     * @description Whether consumed offsets are committed periodically in the background.
     * @default true
     */
    @MCAttribute
    public void setEnableAutoCommit(boolean enableAutoCommit) {
        this.enableAutoCommit = enableAutoCommit;
    }

    public boolean isEnableAutoCommit() {
        return enableAutoCommit;
    }

    /**
     * @description Maximum number of records returned by a single poll.
     * @default 500
     */
    @MCAttribute
    public void setMaxPollRecords(int maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
    }

    public int getMaxPollRecords() {
        return maxPollRecords;
    }

    /**
     * @description Time in milliseconds after which a consumer without heartbeat is considered dead.
     * @default 45000
     */
    @MCAttribute
    public void setSessionTimeoutMs(int sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /**
     * @description Maximum time in milliseconds between two polls, i.e. the time available to process a batch of messages.
     * @default 300000
     */
    @MCAttribute
    public void setMaxPollIntervalMs(int maxPollIntervalMs) {
        this.maxPollIntervalMs = maxPollIntervalMs;
    }

    public int getMaxPollIntervalMs() {
        return maxPollIntervalMs;
    }

    /**
     * @description Minimum amount of data in bytes the broker returns for a fetch.
     * @default 1
     */
    @MCAttribute
    public void setFetchMinBytes(int fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    public int getFetchMinBytes() {
        return fetchMinBytes;
    }

    /**
     * @description Maximum time in milliseconds the broker waits for fetchMinBytes to be available.
     * @default 500
     */
    @MCAttribute
    public void setFetchMaxWaitMs(int fetchMaxWaitMs) {
        this.fetchMaxWaitMs = fetchMaxWaitMs;
    }

    public int getFetchMaxWaitMs() {
        return fetchMaxWaitMs;
    }

    /**
     * @description Protocol used to talk to the brokers. Kafka default is PLAINTEXT.
     * @example PLAINTEXT | SSL | SASL_PLAINTEXT | SASL_SSL
     */
    @MCAttribute
    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;
    }

    public String getSecurityProtocol() {
        return securityProtocol;
    }

    /**
     * @description SASL mechanism for authentication, used with the security protocols SASL_PLAINTEXT and SASL_SSL.
     * @example PLAIN | SCRAM-SHA-256 | SCRAM-SHA-512
     */
    @MCAttribute
    public void setSaslMechanism(String saslMechanism) {
        this.saslMechanism = saslMechanism;
    }

    public String getSaslMechanism() {
        return saslMechanism;
    }

    /**
     * @description JAAS login configuration for SASL authentication.
     * @example org.apache.kafka.common.security.plain.PlainLoginModule required username="user" password="secret";
     */
    @MCAttribute
    public void setSaslJaasConfig(String saslJaasConfig) {
        this.saslJaasConfig = saslJaasConfig;
    }

    public String getSaslJaasConfig() {
        return saslJaasConfig;
    }
}
