package com.predic8.membrane.core.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class YamlUtilTest {

    @Test
    void removesOnlyFirstYamlDocStartMarker() {
        String input = """
            ---
            kind: api
            name: users
            ---
            port: 8080
            """;

        String expected = """
            kind: api
            name: users
            ---
            port: 8080
            """;

        String result = YamlUtil.removeFirstYamlDocStartMarker(input);

        assertEquals(expected, result);
    }

    @Test
    void leavesContentUntouchedIfNoMarkerPresent() {
        String input = """
            kind: api
            name: users
            port: 8080
            """;

        assertEquals(input, YamlUtil.removeFirstYamlDocStartMarker(input));
    }

    @Test
    void handlesLeadingSpacesBeforeMarker() {
        String input = """
              ---
            key: value
            """;

        assertEquals("key: value\n", YamlUtil.removeFirstYamlDocStartMarker(input));
    }

    @Test
    void returnsNullIfInputIsNull() {
        assertNull(YamlUtil.removeFirstYamlDocStartMarker(null));
    }
}
