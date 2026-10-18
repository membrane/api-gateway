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
import com.predic8.membrane.core.router.Router;

import java.util.Map;

/**
 * Receives messages from Kafka. The Membrane Kafka Extension provides the implementation;
 * without it the kafkaConsumer fails on startup with a ConfigurationException.
 * <p>
 * A record is acknowledged by committing its offset. With {@link AckMode#CLIENT}, the implementation commits according
 * to the {@link Result} of the handler, and delivery is at least once: after a crash between delivery and commit, a
 * record is delivered again. With {@link AckMode#AUTO}, Kafka commits in the background and the implementation does
 * not commit; {@link Result#RETRY} still hands the record over again, but a record can be lost on a crash.
 */
public interface KafkaConsumerComponentExtension {

    /**
     * Name of the exchange property holding the record key as <code>byte[]</code>. Not set if the record has no key.
     */
    String KEY_PROPERTY = "membrane.ext.kafka.key";

    /**
     * When the offset of a record is committed.
     */
    enum AckMode {
        /**
         * Kafka commits the offsets of received records periodically in the background, see the property
         * <code>enable.auto.commit</code>, which is <code>true</code> in this mode.
         */
        AUTO,
        /**
         * The implementation commits the offset of a record after the handler returned {@link Result#ACK} or
         * {@link Result#REJECT}, or the record was skipped after the last retry. <code>enable.auto.commit</code> is
         * <code>false</code> in this mode.
         */
        CLIENT
    }

    /**
     * What the implementation does with a record after the handler returned. A Kafka commit acknowledges all
     * records of a partition up to the committed offset, so a record is never skipped silently: the next record of
     * a partition is only handed over once the previous one is acknowledged or rejected.
     */
    enum Result {
        /**
         * Delivered. Commit the offset of the record + 1, unless the ack mode is {@link AckMode#AUTO}.
         */
        ACK,
        /**
         * Not delivered for a reason that may go away, e.g. the target is down or answered with a server error,
         * 408 or 429.
         * Do not commit. Seek back to the record, wait and hand it over again, up to <code>maxRetries</code>
         * times, see {@link #start(String, int, AckMode, MessageHandler)}.
         */
        RETRY,
        /**
         * The target refused the message itself, e.g. with a client error. Sending it again does not help.
         * Commit the offset of the record + 1 to skip it, unless the ack mode is {@link AckMode#AUTO}.
         */
        REJECT
    }

    /**
     * Called for every received record. The consumer implementation creates the exchange.
     */
    interface MessageHandler {
        /**
         * The request is sent to the target of the kafkaConsumer like a request that came in over HTTP, so it
         * must look like one: method POST and URI <code>/</code>, so that the path of the target URL is used
         * unchanged.
         *
         * @param exc Exchange created from the record: the record value is the request body and the record key,
         *            if present, is in the property {@link #KEY_PROPERTY}
         * @return what to do with the record. Does not throw; an unexpected error is returned as {@link Result#RETRY}.
         */
        Result handle(Exchange exc);
    }

    /**
     * The router is passed so that an implementation can reach the other parts of the gateway. Later it could ask
     * the router whether the target is up, and pause consuming until it is.
     *
     * @param router     Router the consumer belongs to
     * @param properties Kafka consumer properties using the original Kafka names, e.g. <code>bootstrap.servers</code>
     */
    void init(Router router, Map<String, String> properties);

    /**
     * Starts consuming. Every record of the topic is passed to the handler.
     * <p>
     * Not to be forgotten by the implementation:
     * <ul>
     * <li>This method is called from the init of the kafkaConsumer, before the interceptors of its flow and the
     * other APIs are initialized. Do not hand records to the handler before the router is running, see
     * {@link Router#isRunning()}.</li>
     * <li>The method must not let an exception escape that could harm the caller, for example from unreachable
     * brokers or wrong credentials. Catch, log and retry or give up.</li>
     * <li>{@link #stop()} can run while this method is still starting. Make sure no consumer is left running
     * after stop.</li>
     * </ul>
     *
     * @param topic      Topic to subscribe to
     * @param maxRetries How often a record is handed over again after the handler returned {@link Result#RETRY}.
     *                   When the handler still returns {@link Result#RETRY} after the last retry, log an error with
     *                   the topic, partition and offset of the record and skip it like {@link Result#REJECT}.
     *                   A negative value retries without limit.
     * @param ackMode    When to commit the offset of a record. Matches <code>enable.auto.commit</code> in the
     *                   properties passed to {@link #init}.
     * @param handler    Receives the records
     */
    void start(String topic, int maxRetries, AckMode ackMode, MessageHandler handler);

    /**
     * Stops consuming and releases the connections. Called when the router stops, and when the kafkaConsumer is
     * initialized again and replaces this instance. Can be called more than once and before {@link #start} finished.
     */
    void stop();
}
