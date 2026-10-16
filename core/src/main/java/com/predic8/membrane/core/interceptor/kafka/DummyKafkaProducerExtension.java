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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Placeholder used when the Membrane Kafka Extension is not installed. Sends nothing, only logs.
 */
public class DummyKafkaProducerExtension implements KafkaProducerExtension {

    private static final Logger log = LoggerFactory.getLogger(DummyKafkaProducerExtension.class);

    @Override
    public void init(final Map<String, String> properties) {
        log.warn("The KafkaProducer extension is not installed. Get it from predic8 (info@predic8.de)");
    }

    @Override
    public void send(final String topic, final byte[] key, final Exchange exc) throws IOException {
        log.info("Dummy Kafka producer called: topic={}, key={}, value={} bytes", topic, key == null ? "none" : "%d bytes".formatted(key.length), exc.getRequest().getBody().getContent().length);
    }

    @Override
    public void stop() {
        log.info("Dummy Kafka producer stopped");
    }
}
