package com.predic8.membrane.annot.generator.kubernetes.model;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaUtils.entryToJson;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;

class SchemaUtilsTest {

    @Test
    void string() {
        assertEquals("""
                "foo": "bar\"""", entryToJson(entry("foo", "bar")));
    }

    @Test
    void listNumbers() {
        assertEquals("""
                "enum": [1,2,3]""", entryToJson(entry("enum", List.of(1,2,3))));
    }

    @Test
    void list() {
        assertEquals("""
                "enum": ["a","b"]""", entryToJson(entry("enum", List.of("a","b"))));
    }

    @Test
    void stringWithQuotes() {
        assertEquals("\"foo\": \"bar\\\"baz\"", entryToJson(entry("foo", "bar\"baz")));
    }

    @Test
    void stringWithBackslash() {
        assertEquals("\"foo\": \"bar\\\\baz\"", entryToJson(entry("foo", "bar\\baz")));
    }

    @Test
    void keyWithQuotes() {
        assertEquals("\"foo\"bar\": \"baz\"", entryToJson(entry("foo\"bar", "baz")));
    }

    @Test
    void refObj() {
        assertEquals("\"$ref\": \"#/$defs/foo\"", entryToJson(entry("$ref", new RefObj("#/$defs/foo"))));
    }

}