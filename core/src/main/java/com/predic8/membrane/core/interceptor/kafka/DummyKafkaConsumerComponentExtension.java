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

import com.predic8.membrane.core.router.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Placeholder used when the Membrane Kafka Extension is not installed. Receives nothing, only logs.
 */
public class DummyKafkaConsumerComponentExtension implements KafkaConsumerComponentExtension {

    private static final Logger log = LoggerFactory.getLogger(DummyKafkaConsumerComponentExtension.class);

    @Override
    public void init(final Router router, final Map<String, String> properties) {
        log.warn("The KafkaConsumer extension is not installed. Get it from predic8 (info@predic8.de)");
    }

    @Override
    public void start(final String topic, final MessageHandler handler) {
        log.info("Dummy Kafka consumer called: would consume topic={}, no messages will be received", topic);
    }

    @Override
    public void stop() {
        log.info("Dummy Kafka consumer stopped");
    }
}
