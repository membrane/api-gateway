package com.predic8.membrane.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.predic8.membrane.core.util.StringList.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit‑tests for {@link StringList}.
 */
class StringListTest {

    @Nested
    @DisplayName("parseToList")
    class ParseToList {

        @Test
        void nullInputYieldsEmptyList() {
            assertEquals(List.of(), parseToList(null));
        }

        @Test
        void blankInputYieldsEmptyList() {
            assertEquals(List.of(), parseToList("   "));
        }

        @Test
        void commaSeparated() {
            List<String> expected = List.of("GET", "POST", "PUT");
            assertEquals(expected, parseToList("GET,POST, PUT"));
        }

        @Test
        void spaceSeparated() {
            List<String> expected = List.of("GET", "POST", "PUT");
            assertEquals(expected, parseToList("GET POST  PUT"));
        }

        @Test
        void mixedSeparatorsAndExtraWhitespace() {
            List<String> expected = List.of("A", "B", "C");
            assertEquals(expected, parseToList(" A ,  B C ,  ,  "));
        }
    }

    @Nested
    @DisplayName("parseToSet")
    class ParseToSet {

        @Test
        void deduplicatesAndPreservesOrder() {
            Set<String> actual = parseToSet("X Y X Z");
            assertEquals(new LinkedHashSet<>(List.of("X", "Y", "Z")), actual);
        }
    }

    @Nested
    @DisplayName("generic parse")
    class GenericParse {

        @Test
        void customCollection() {
            Deque<String> deque = parse("alpha, beta  gamma", ArrayDeque::new);
            assertIterableEquals(List.of("alpha", "beta", "gamma"), deque);
        }
    }
}

