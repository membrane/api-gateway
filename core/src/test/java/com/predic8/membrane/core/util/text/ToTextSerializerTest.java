package com.predic8.membrane.core.util.text;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.text.SerializationFunction.TEXT_SERIALIZATION;
import static org.junit.jupiter.api.Assertions.*;

class ToTextSerializerTest {

    @Test
    void nullCheck() {
        assertEquals("null", TEXT_SERIALIZATION.apply(null));
    }

    @Test
    void string() {
        assertEquals("foo", TEXT_SERIALIZATION.apply("foo"));
    }
}