package com.predic8.membrane.core.interceptor.httpcache;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DirectiveArgumentEqualsTest {

    @Test
    void testEqualsSelf() {
        DirectiveArgument<?> arg = new DirectiveArgument<>(1414);

        assertEquals(arg, arg);
    }

    @Test
    void testEquals() {
        DirectiveArgument<?> arg = new DirectiveArgument<>(1414);
        DirectiveArgument<?> arg2 = new DirectiveArgument<>(1414);

        assertEquals(arg, arg2);
    }

    @Test
    void testNotEquals() {
        DirectiveArgument<?> arg = new DirectiveArgument<>(1414);
        DirectiveArgument<?> arg2 = new DirectiveArgument<>(8080);

        assertNotEquals(arg, arg2);
    }

    @Test
    void testNotEqualsNull() {
        DirectiveArgument<?> arg = new DirectiveArgument<>(1414);

        assertNotEquals(arg, null);
    }

    @Test
    void testNotEqualsWrongClass() {
        DirectiveArgument<?> arg = new DirectiveArgument<>(1414);
        String arg2 = "";

        assertNotEquals(arg, arg2);
    }
}
