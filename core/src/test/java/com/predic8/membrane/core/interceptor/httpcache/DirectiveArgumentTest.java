package com.predic8.membrane.core.interceptor.httpcache;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

class DirectiveArgumentTest {

    @Test
    void parseInt() {
        DirectiveArgument<?> dir = DirectiveArgument.parse("1");

        assertInstanceOf(Integer.class, dir.getValue());
    }

    @Test
    void parseSingleItemList() {
        DirectiveArgument<?> dir = DirectiveArgument.parse("\"test\"");

        assertInstanceOf(LinkedList.class, dir.getValue());
    }

    @Test
    void parseList() {
        DirectiveArgument<?> dir = DirectiveArgument.parse("\"test, Test, TEST\"");

        assertInstanceOf(LinkedList.class, dir.getValue());
    }

    @Test
    void parseDefaultOnNegative() {
        DirectiveArgument<?> dir = DirectiveArgument.parse("-100");

        assertEquals(0, dir.getValue());
    }

    @Test
    void parseDefaultOnInvalid() {
        DirectiveArgument<?> dir = DirectiveArgument.parse("Demo");

        assertEquals(0, dir.getValue());
    }
}