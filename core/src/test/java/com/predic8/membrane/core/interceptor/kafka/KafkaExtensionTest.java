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
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaExtensionTest {

    public static class ProducerImpl implements KafkaProducerExtension {
        @Override
        public void init(final Map<String, String> properties) {
        }

        @Override
        public void send(final String topic, final byte[] key, final Exchange exc) {
        }

        @Override
        public void stop() {
        }
    }

    @Test
    void loadsClassOnClasspath() {
        final var producer = KafkaExtension.load(ProducerImpl.class.getName(), KafkaProducerExtension.class, "missing");
        assertEquals(ProducerImpl.class, producer.getClass());
    }

    @Test
    void rejectsMissingClass() {
        final var e = assertThrows(ConfigurationException.class, () -> KafkaExtension.load(KafkaExtension.PRODUCER_CLASS, KafkaProducerExtension.class, "missing"));
        assertEquals("missing", e.getMessage());
    }

    /**
     * The class file is present but invalid, so this is no missing extension and the cause must be kept.
     */
    @Test
    void reportsClassThatCannotBeLoaded() {
        final var e = assertThrows(ConfigurationException.class, () -> KafkaExtension.load(KafkaExtensionTest.class.getPackageName() + ".BrokenExtension", KafkaProducerExtension.class, "missing"));
        assertNotEquals("missing", e.getMessage());
        assertInstanceOf(LinkageError.class, e.getCause());
    }

    @Test
    void rejectsClassOfWrongType() {
        assertThrows(ConfigurationException.class, () -> KafkaExtension.load("java.lang.String", KafkaProducerExtension.class, "missing"));
    }
}
