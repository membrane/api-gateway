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

import java.io.IOException;
import java.util.Map;

/**
 * Sends messages to Kafka. The Membrane Kafka Extension provides the real implementation;
 * without it {@link DummyKafkaProducerExtension} is used.
 */
public interface KafkaProducerExtension {

    /**
     * @param properties Kafka producer properties using the original Kafka names, e.g. <code>bootstrap.servers</code>
     */
    void init(Map<String, String> properties);

    /**
     * @param topic Target topic
     * @param key   Record key as UTF-8 bytes, may be null
     * @param exc   Exchange whose request body is the record value
     */
    void send(String topic, byte[] key, Exchange exc) throws IOException;

    /**
     * Sends pending messages and releases the connections. Called when the router stops.
     */
    void stop();
}
