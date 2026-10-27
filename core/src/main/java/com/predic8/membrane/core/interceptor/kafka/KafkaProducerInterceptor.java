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
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractLanguageInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpression;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;
import static com.predic8.membrane.core.util.ExtensionLoader.load;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.exception.ExceptionUtils.throwableOfType;

/**
 * @description <p>Publishes the body of the request as a message to an Apache Kafka topic.</p>
 *
 * <p>The interceptor needs the Membrane Kafka Extension. Without the extension Membrane does not start.
 * Contact predic8 for the extension.</p>
 * @yaml
 * <pre><code>
 * kafkaProducer:
 *   bootstrapServers: kafka.example.com:9092
 *   topic: orders
 *   key: headers['X-Order-Id']
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "kafkaProducer")
public class KafkaProducerInterceptor extends AbstractLanguageInterceptor {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerInterceptor.class);

    private static final String PRODUCER_CLASS = "io.membrane_api.ext.kafka.KafkaProducerComponentImpl";

    private String bootstrapServers;
    private String topic;
    private String key;
    private String clientId;
    private String acks = "all";
    private String compressionType = "none";
    private boolean enableIdempotence = true;
    private int batchSize = 16384;
    private int lingerMs = 0;
    private int requestTimeoutMs = 30000;
    private int deliveryTimeoutMs = 120000;
    private int maxRequestSize = 1048576;
    private String securityProtocol;
    private String saslMechanism;
    private String saslJaasConfig;

    private ExchangeExpression keyExpression;

    /**
     * Written in init(), read-only afterwards.
     */
    private KafkaProducerExtension producer;

    @Override
    public void init() {
        super.init();
        if (key != null)
            keyExpression = expression(this, language, key);
        // init() can run again, e.g. when the API is re-initialized
        destroy();
        producer = createProducer();
        producer.init(producerProperties());
    }

    /**
     * Package-private so that tests can replace the extension with a stub.
     */
    KafkaProducerExtension createProducer() {
        return load(PRODUCER_CLASS, KafkaProducerExtension.class,
                "The Membrane Kafka Extension is needed for the kafkaProducer interceptor. Ask info@predic8.de.");
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
        props.put("bootstrap.servers", bootstrapServers);
        props.put("acks", acks);
        props.put("compression.type", compressionType);
        props.put("enable.idempotence", String.valueOf(enableIdempotence));
        props.put("batch.size", String.valueOf(batchSize));
        props.put("linger.ms", String.valueOf(lingerMs));
        props.put("request.timeout.ms", String.valueOf(requestTimeoutMs));
        props.put("delivery.timeout.ms", String.valueOf(deliveryTimeoutMs));
        props.put("max.request.size", String.valueOf(maxRequestSize));
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

    @Override
    public Outcome handleRequest(final Exchange exc) {
        try {
            producer.send(topic, evaluateKey(exc), exc);
            return CONTINUE;
        } catch (Exception e) {
            // An unreadable body (e.g. invalid Content-Encoding) is answered by FlowController
            final var bodyFailure = throwableOfType(e, ReadingBodyException.class);
            if (bodyFailure != null)
                throw bodyFailure;
            log.error("Could not send message to Kafka topic {}.", topic, e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not send message to Kafka.")
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private byte[] evaluateKey(final Exchange exc) {
        if (keyExpression == null)
            return null;
        final var evaluated = keyExpression.evaluate(exc, REQUEST, String.class);
        return evaluated == null ? null : evaluated.getBytes(UTF_8);
    }

    @Override
    public String getDisplayName() {
        return "Kafka Producer";
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
     * @description Topic the messages are sent to.
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
     * @description Expression computing the record key. The key determines the partition. Without a key Kafka
     * distributes the messages over the partitions.
     * @example headers['X-Order-Id']
     */
    @MCAttribute
    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
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
     * @description Number of brokers that must acknowledge a message before it counts as sent.
     * @default all
     * @example 0 | 1 | all
     */
    @MCAttribute
    public void setAcks(String acks) {
        this.acks = acks;
    }

    public String getAcks() {
        return acks;
    }

    /**
     * @description Compression of the message batches.
     * @default none
     * @example none | gzip | snappy | lz4 | zstd
     */
    @MCAttribute
    public void setCompressionType(String compressionType) {
        this.compressionType = compressionType;
    }

    public String getCompressionType() {
        return compressionType;
    }

    /**
     * @description Prevents duplicate messages when the producer retries. Requires acks=all.
     * @default true
     */
    @MCAttribute
    public void setEnableIdempotence(boolean enableIdempotence) {
        this.enableIdempotence = enableIdempotence;
    }

    public boolean isEnableIdempotence() {
        return enableIdempotence;
    }

    /**
     * @description Maximum size of a batch of messages in bytes.
     * @default 16384
     */
    @MCAttribute
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * @description Time in milliseconds to wait for more messages before a batch is sent.
     * @default 0
     */
    @MCAttribute
    public void setLingerMs(int lingerMs) {
        this.lingerMs = lingerMs;
    }

    public int getLingerMs() {
        return lingerMs;
    }

    /**
     * @description Time in milliseconds to wait for the answer of a broker.
     * @default 30000
     */
    @MCAttribute
    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * @description Upper bound in milliseconds for reporting success or failure of a send, including retries.
     * @default 120000
     */
    @MCAttribute
    public void setDeliveryTimeoutMs(int deliveryTimeoutMs) {
        this.deliveryTimeoutMs = deliveryTimeoutMs;
    }

    public int getDeliveryTimeoutMs() {
        return deliveryTimeoutMs;
    }

    /**
     * @description Maximum size of a single message in bytes.
     * @default 1048576
     */
    @MCAttribute
    public void setMaxRequestSize(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public int getMaxRequestSize() {
        return maxRequestSize;
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
