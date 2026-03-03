package com.predic8.membrane.core.util.text;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.text.ToURLSerializer.toURL;
import static org.junit.jupiter.api.Assertions.*;

class ToURLSerializerTest {

    @Test
    void nullInput() {
        assertEquals("null", toURL(null));
    }

    @Test
    void encodesPlainString() {
        assertEquals("hello+world", toURL("hello world"));
    }

    @Test
    void encodesSpecialCharacters() {
        assertEquals("%26%3F%C3%A4%C3%B6%C3%BC%21%22%3D%3A%23%2F%5C", toURL("&?äöü!\"=:#/\\"));
    }

    @Test
    void encodesUnicode() {
        assertEquals("M%C3%BCller", toURL("Müller"));
    }

    @Test
    void encodesNumberViaTextSerialization() {
        assertEquals("123", toURL(123));
    }

    @Test
    void encodesBooleanViaTextSerialization() {
        assertEquals("true", toURL(true));
    }

    @Test
    void encodesNullViaTextSerialization() {
        // depends on TEXT_SERIALIZATION(null) → typically "null"
        assertEquals("null", toURL(null));
    }
}