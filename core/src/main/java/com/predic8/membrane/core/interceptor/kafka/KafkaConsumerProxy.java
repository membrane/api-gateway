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
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.AckMode;
import com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.Result;
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
import static com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.AckMode.AUTO;
import static com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.AckMode.CLIENT;
import static com.predic8.membrane.core.interceptor.kafka.KafkaConsumerComponentExtension.Result.*;
import static com.predic8.membrane.core.interceptor.kafka.KafkaExtension.CONSUMER_CLASS;
import static com.predic8.membrane.core.interceptor.kafka.KafkaExtension.load;

/**
 * @description <p>Consumes messages from an Apache Kafka topic. Every message runs through the flow of this API and
 * is sent to its target, like a request that came in over HTTP. The target can be an HTTP backend or another API
 * using <code>internal://</code>.</p>
 *
 * <p>A message is delivered when the target answers with a status below 400. A client error skips the message,
 * since sending it again would not help. A server error, an unreachable target, 408 Request Timeout and 429 Too Many
 * Requests deliver the message again until it succeeds or maxRetries is reached; until then, the following messages
 * of the same partition wait.</p>
 *
 * <p>By default a message is acknowledged to Kafka only after it was delivered or skipped, so no message is lost, but
 * it can be delivered more than once and the target should handle duplicates. With automatic acknowledgement, Kafka
 * commits received messages in the background instead.</p>
 *
 * <p>The API does not support hot deployment and retryInit. Hot deployment is on by default, so turn it off with
 * <code>hotDeploy: false</code> under <code>configuration</code>; retryInit is off by default.</p>
 *
 * <p>The API needs the Membrane Kafka Extension. Without the extension Membrane does not start.
 * Contact predic8 for the extension.</p>
 * @yaml
 * <pre><code>
 * kafkaConsumer:
 *   name: orders-in
 *   bootstrapServers: kafka.example.com:9092
 *   topic: orders
 *   groupId: membrane
 *   flow:
 *     - setHeader:
 *         name: X-Source
 *         value: kafka
 *   target:
 *     url: https://orders.example.com/orders
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "kafkaConsumer", topLevel = true, component = false)
public class KafkaConsumerProxy extends AbstractServiceProxy implements NotPortOpeningProxy {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerProxy.class);

    private String bootstrapServers;
    private String topic;
    private String groupId;
    private String clientId;
    private AckMode ackMode = CLIENT;
    private int maxRetries = -1;
    private String autoOffsetReset = "latest";
    private int maxPollRecords = 500;
    private int sessionTimeoutMs = 45000;
    private int maxPollIntervalMs = 300000;
    private int fetchMinBytes = 1;
    private int fetchMaxWaitMs = 500;
    private String securityProtocol;
    private String saslMechanism;
    private String saslJaasConfig;

    /**
     * Shared with clones, since clone() copies the reference: with retryInit the router replaces this instance with
     * a clone, but only this instance is registered for @PreDestroy, so destroy() has to reach the consumer the
     * clone started.
     */
    private final AtomicReference<KafkaConsumerComponentExtension> consumer = new AtomicReference<>();

    public KafkaConsumerProxy() {
        key = new InternalProxyKey();
    }

    /**
     * Called before the interceptors of the flow are initialized. The extension waits until the router is running
     * before it hands over messages, see {@link KafkaConsumerComponentExtension#start(String, int, AckMode, KafkaConsumerComponentExtension.MessageHandler)}.
     * <p>
     * Can run more than once: when the API is inactive and retryInit is on, the router clones it, and the clone
     * shares the consumer of the old instance and is initialized twice. The running consumer is stopped first, so
     * only one consumer of the group keeps running.
     */
    @Override
    public void init() {
        super.init();
        if (name == null || name.isEmpty())
            name = topic + "-" + groupId;
        ((InternalProxyKey) key).setServiceName(getName());
        checkRouterConfiguration();
        stopConsumer();
        final var created = createConsumer();
        consumer.set(created);
        created.init(router, consumerProperties());
        created.start(topic, maxRetries, ackMode, this::handle);
    }

    /**
     * Package-private so that tests can replace the extension with a stub.
     */
    KafkaConsumerComponentExtension createConsumer() {
        return load(CONSUMER_CLASS, KafkaConsumerComponentExtension.class,
                "The Membrane Kafka Extension is needed for kafkaConsumer. Ask info@predic8.de.");
    }

    /**
     * Until Membrane 8 has a lifecycle for APIs, both options would leave a consumer running:
     * <ul>
     * <li>hotDeploy: the consumer is only stopped through @PreDestroy, which runs when the router stops. Changing or
     * removing the API leaves the old consumer receiving messages.</li>
     * <li>retryInit: the consumer starts before the interceptors of the flow are initialized. If one of them fails,
     * the API stays inactive with a running consumer that does not deliver, and every retry restarts it.</li>
     * </ul>
     */
    private void checkRouterConfiguration() {
        final var configuration = router.getConfiguration();
        if (configuration.isHotDeploy())
            throw new ConfigurationException("kafkaConsumer %s does not support hot deployment: a changed or removed consumer would keep receiving messages until Membrane stops. Set hotDeploy to false and restart Membrane after changes.".formatted(getName()));
        if (configuration.isRetryInit())
            throw new ConfigurationException("kafkaConsumer %s does not support retryInit: while the API is inactive its consumer would keep reading without delivering. Set retryInit to false.".formatted(getName()));
    }

    @PreDestroy
    public void destroy() {
        stopConsumer();
    }

    private void stopConsumer() {
        final var running = consumer.getAndSet(null);
        if (running != null)
            running.stop();
    }

    private Map<String, String> consumerProperties() {
        final var props = new HashMap<String, String>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("auto.offset.reset", autoOffsetReset);
        props.put("enable.auto.commit", String.valueOf(ackMode == AUTO));
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
     * Runs a received message through the same flow as a request that came in over HTTP. The API is assigned
     * beforehand, so the flow skips matching a request to an API and continues with the flow and the target of
     * this API.
     * <p>
     * Afterwards the response body is read, like the server does when it writes the response to the client, so the
     * connection to the target goes back to the pool.
     */
    Result handle(final Exchange exc) {
        if (!isActive()) {
            log.warn("kafkaConsumer {} is not active. Message from topic {} will be delivered again.", getName(), topic);
            return RETRY;
        }
        try {
            assignRule(exc, this);
            final var outcome = router.getFlowController().invokeRequestHandlers(exc, router.getTransport().getFlow());
            final var response = exc.getResponse();
            if (response != null)
                response.discardBody();
            final var result = result(outcome, response);
            if (result == ACK) {
                exc.setCompleted();
                return ACK;
            }
            final var status = response == null ? "no response" : "status " + response.getStatusCode();
            exc.finishExchange(true, status);
            if (result == REJECT)
                log.warn("Target of kafkaConsumer {} rejected message from topic {} with {}. Skipping it.", getName(), topic, status);
            else
                log.info("Delivering message from topic {} by kafkaConsumer {} failed with {}. Delivering it again.", topic, getName(), status);
            return result;
        } catch (Exception e) {
            log.warn("Error delivering message from topic {} by kafkaConsumer {}. Delivering it again.", topic, getName(), e);
            exc.finishExchange(true, e.getMessage());
            return RETRY;
        }
    }

    /**
     * A client error means the message itself is wrong, so it is not delivered again. Everything else that is not a
     * success may go away: a server error, an unreachable target, and the client errors 408 Request Timeout and
     * 429 Too Many Requests.
     */
    private static Result result(final Outcome outcome, final Response response) {
        if (response == null)
            return RETRY;
        final var status = response.getStatusCode();
        if (status >= 400 && status < 500 && status != 408 && status != 429)
            return REJECT;
        if (status < 400 && outcome != ABORT)
            return ACK;
        return RETRY;
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
     * @description Consumer group the API joins. Consumers of the same group share the partitions of the topic.
     * @example membrane
     */
    @Required
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
     * @description When a message is acknowledged to Kafka. With <code>client</code>, a message is acknowledged after
     * it was delivered or skipped. With <code>auto</code>, Kafka acknowledges received messages periodically in the
     * background, so a message counts as consumed once it is received, and it can be lost if Membrane stops before it
     * is delivered.
     * @default client
     * @example auto
     */
    @MCAttribute
    public void setAckMode(AckMode ackMode) {
        this.ackMode = ackMode;
    }

    public AckMode getAckMode() {
        return ackMode;
    }

    /**
     * @description How often a message is delivered again after a server error, an unreachable target, 408 or 429.
     * When the last retry fails too, an error is logged and the message is skipped. A negative value retries until
     * the message is delivered.
     * @default -1
     * @example 10
     */
    @MCAttribute
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getMaxRetries() {
        return maxRetries;
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
