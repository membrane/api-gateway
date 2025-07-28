/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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

