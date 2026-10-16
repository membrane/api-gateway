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

import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaExtensionTest {

    @Test
    void detectsClassOnClasspath() {
        assertTrue(KafkaExtension.isOnClasspath("java.lang.String"));
    }

    public static class ProducerImpl extends DummyKafkaProducerExtension {
    }

    @Test
    void loadsClassOnClasspath() {
        final var producer = KafkaExtension.load(ProducerImpl.class.getName(), KafkaProducerExtension.class, DummyKafkaProducerExtension::new);
        assertEquals(ProducerImpl.class, producer.getClass());
    }

    @Test
    void loadsFallbackIfClassIsMissing() {
        final var producer = KafkaExtension.load(KafkaExtension.PRODUCER_CLASS, KafkaProducerExtension.class, DummyKafkaProducerExtension::new);
        assertEquals(DummyKafkaProducerExtension.class, producer.getClass());
    }

    @Test
    void rejectsClassOfWrongType() {
        assertThrows(ConfigurationException.class, () -> KafkaExtension.load("java.lang.String", KafkaProducerExtension.class, DummyKafkaProducerExtension::new));
    }

    @Test
    void detectsMissingClass() {
        assertFalse(KafkaExtension.isOnClasspath(KafkaExtension.CONSUMER_CLASS));
        assertFalse(KafkaExtension.isOnClasspath(KafkaExtension.PRODUCER_CLASS));
    }
}
