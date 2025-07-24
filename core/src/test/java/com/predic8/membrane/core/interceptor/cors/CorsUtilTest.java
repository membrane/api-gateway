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

package com.predic8.membrane.core.interceptor.cors;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.cors.CorsUtil.normalizeOrigin;
import static com.predic8.membrane.core.interceptor.cors.CorsUtil.splitBySpace;
import static org.junit.jupiter.api.Assertions.*;

class CorsUtilTest {

    @Test
    void testNormalizeOrigin() {
        assertEquals("/foo/bar", normalizeOrigin("/foo/bar//"));
        assertEquals("bar", normalizeOrigin("bar"));
        assertEquals("", normalizeOrigin(""));
        assertEquals("", normalizeOrigin("/"));
        assertEquals("", normalizeOrigin("//"));
        assertEquals("/foo", normalizeOrigin("/foo/"));

        assertEquals("http://example.com", normalizeOrigin("HTTP://EXAMPLE.COM"));
        assertEquals("https://api.test.com/path", normalizeOrigin("HTTPS://API.TEST.COM/PATH/"));
    }


    @Test
    void splitStringBySpace() {
        assertEquals(Set.of("a", "b", "c"), splitBySpace(" a a  b   c "));
        assertEquals(Set.of(), splitBySpace(""));
        assertEquals(Set.of(), splitBySpace("   "));
        assertEquals(Set.of("single"), splitBySpace("single"));
        assertEquals(Set.of("a"), splitBySpace("a a a")); // duplicates handled
    }
}