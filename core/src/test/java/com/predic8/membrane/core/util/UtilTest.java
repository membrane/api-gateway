package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.Util.lineCount;
import static org.junit.jupiter.api.Assertions.*;

class UtilTest {

    @Test
    void lineCountTest() {
        assertEquals(2, lineCount("""
                wow!
                lines!
                """));
    }
}