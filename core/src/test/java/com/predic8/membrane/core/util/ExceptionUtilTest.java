package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.ExceptionUtil.concatMessageAndCauseMessages;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExceptionUtilTest {

    @Test
    public void testSimple() {
        assertEquals("foo",
                concatMessageAndCauseMessages(new RuntimeException("foo")));
    }

    @Test
    public void testLevel2() {
        assertEquals("foo caused by: bar",
                concatMessageAndCauseMessages(new RuntimeException("foo", new RuntimeException("bar"))));
    }
    @Test

    public void testLevel3() {
        assertEquals("foo caused by: bar caused by: baz",
                concatMessageAndCauseMessages(new RuntimeException("foo", new RuntimeException("bar", new RuntimeException("baz")))));
    }
}
