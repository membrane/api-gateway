package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.StringUtil.truncateAfter;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {

    private static final String POEM = "To the greene forest so pleasant and faire";

    @Test
    void truncateAfterTest() {
        assertEquals("", truncateAfter(POEM,0));
        assertEquals("To the greene", truncateAfter(POEM,13));
        assertEquals(POEM, truncateAfter(POEM,POEM.length()));
        assertEquals(POEM, truncateAfter(POEM,1000));
    }
}