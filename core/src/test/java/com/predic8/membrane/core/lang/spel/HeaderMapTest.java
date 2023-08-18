package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.http.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HeaderMapTest {

    HeaderMap headerMap;

    @BeforeEach
    void setUp() {
        var headers = new Header();
        headers.add("foo", "bar");
        headers.add("qux", "baz");

        headerMap = new HeaderMap(headers);
    }

    @Test
    void size() {
        assertEquals(2, headerMap.size());
    }

    @Test
    void isEmpty() {
        assertFalse(headerMap.isEmpty());
    }

    @Test
    void containsKey() {
        assertTrue(headerMap.containsKey("foo"));
        assertTrue(headerMap.containsKey("FOO"));
        assertFalse(headerMap.containsKey("moo"));
    }

    @Test
    void containsValue() {
        assertTrue(headerMap.containsValue("bar"));
        assertFalse(headerMap.containsKey("BAR"));
    }

    @Test
    void get() {
        var got = headerMap.get("foo");
        assertEquals("bar", got);
    }

    @Test
    void put() {
        headerMap.put("a", "b");
        assertEquals("b", headerMap.get("a"));
    }

    @Test
    void remove() {
        headerMap.remove("foo");
        assertFalse(headerMap.containsKey("foo"));
    }

    @Test
    void putAll() {
        headerMap.putAll(Map.of(
                "qqq", "qqq",
                "www", "www"
        ));

        assertEquals("qqq", headerMap.get("qqq"));
        assertEquals("www", headerMap.get("www"));
    }

    @Test
    void clear() {
        headerMap.clear();
        assertTrue(headerMap.isEmpty());
    }

    @Test
    void keySet() {
        var got = headerMap.keySet();
        assertEquals(Set.of("foo", "qux"), got);
    }

    @Test
    void values() {
        var got = new HashSet<>(headerMap.values());
        assertEquals(new HashSet<>(Set.of("baz", "bar")), got);
    }

    @Test
    void entrySet() {
        var expected = Map.of(
                "foo", "bar",
                "qux", "baz"
        ).entrySet();
        var got = headerMap.entrySet();

        assertEquals(expected, got);
    }
}