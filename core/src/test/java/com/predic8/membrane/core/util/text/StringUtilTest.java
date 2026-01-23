package com.predic8.membrane.core.util.text;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.text.StringUtil.tail;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {

    @Test
    void testTail() {
        assertEquals("def", tail("abcdef",3));
        assertEquals("abcdef", tail("abcdef",10));
        assertEquals("", tail("",10));
    }
}