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
 * Receives messages from Kafka. The Membrane Kafka Extension provides the real implementation;
 * without it {@link DummyKafkaConsumerComponentExtension} is used.
 */
public interface KafkaConsumerComponentExtension {

    /**
     * Name of the exchange property holding the record key as <code>byte[]</code>. Not set if the record has no key.
     */
    String KEY_PROPERTY = "membrane.ext.kafka.key";

    /**
     * Called for every received record. The consumer implementation creates the exchange.
     */
    interface MessageHandler {
        /**
         * @param exc Exchange created from the record: the record value is the request body and the record key,
         *            if present, is in the property {@link #KEY_PROPERTY}
         */
        void handle(Exchange exc) throws Exception;
    }

    /**
     * The router is passed so that an implementation can reach the other parts of the gateway. Later it could ask
     * the router or its rule manager whether the API the messages are forwarded to is up, and pause consuming
     * until it is.
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
     * <li>This method is called from the init of the interceptor, so the API the messages are forwarded to may not
     * be initialized yet. Do not hand records to the handler before the router is running, see
     * {@link Router#isRunning()}, or the forward API is up.</li>
     * <li>The method must not let an exception escape that could harm the caller, for example from unreachable
     * brokers or wrong credentials. Catch, log and retry or give up.</li>
     * <li>{@link #stop()} can run while this method is still starting. Make sure no consumer is left running
     * after stop.</li>
     * </ul>
     *
     * @param topic   Topic to subscribe to
     * @param handler Receives the records
     */
    void start(String topic, MessageHandler handler);

    /**
     * Stops consuming and releases the connections. Called when the router stops.
     */
    void stop();
}
