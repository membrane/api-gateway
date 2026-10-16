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

import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaConsumerInterceptorTest {

    private static KafkaConsumerInterceptor consumer(final String forward) {
        final var i = new KafkaConsumerInterceptor();
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        i.setForward(forward);
        return i;
    }

    @Test
    void missingExtensionFailsOnStartup() {
        final var e = assertThrows(ConfigurationException.class, () -> consumer("internal://handler").init(new DummyTestRouter()));
        assertTrue(e.getMessage().contains("Membrane Kafka Extension"));
    }

    @Test
    void forwardIsRequired() {
        final var e = assertThrows(ConfigurationException.class, () -> consumer(null).init(new DummyTestRouter()));
        assertTrue(e.getMessage().contains("forward"));
    }

    @Test
    void forwardMustBeInternal() {
        final var e = assertThrows(ConfigurationException.class, () -> consumer("http://example.com").init(new DummyTestRouter()));
        assertTrue(e.getMessage().contains("forward"));
    }
}
