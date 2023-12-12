package com.predic8.membrane.core.interceptor.httpcache;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

class CacheControlHeaderTest {

    @Test
    void parseDirectives() {

    }

    @Test
    void httpElementToList() {
        String element = "test, Test = \"DEMO, Demo, demo\", TEST";
        LinkedList<String> list = new LinkedList<>();
        list.add("test");
        list.add("Test = \"DEMO, Demo, demo\"");
        list.add("TEST");

        assertEquals(list, CacheControlHeader.httpElementToList(element));
    }
}