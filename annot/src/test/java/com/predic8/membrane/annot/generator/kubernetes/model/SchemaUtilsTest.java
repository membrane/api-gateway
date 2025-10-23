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
    void list() {
        assertEquals("""
                "enum": [a, b]""", entryToJson(entry("enum", List.of("a","b"))));
    }

}