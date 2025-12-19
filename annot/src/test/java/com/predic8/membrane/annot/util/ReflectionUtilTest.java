package com.predic8.membrane.annot.util;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionUtilTest {


    @Test
    void convertString() {
        var o = ReflectionUtil.convert("abc", String.class);
        assertInstanceOf(String.class, o);
        assertEquals("abc", o);
    }

    @Test
    void convertInteger() {
        var o = ReflectionUtil.convert("123", Integer.class);
        assertInstanceOf(Integer.class, o);
        assertEquals(123, o);
    }

    @Test
    void convertBoolean() {
        var o = ReflectionUtil.convert("true", Boolean.class);
        assertInstanceOf(Boolean.class, o);
        assertEquals(true, o);
    }

    @Test
    void convertNull() {
        var o = ReflectionUtil.convert(null, String.class);
        assertNull(o);
    }

    @Test
    void convertInvalid() {
        assertThrows(RuntimeException.class, () -> ReflectionUtil.convert("abc", Integer.class));
    }
}