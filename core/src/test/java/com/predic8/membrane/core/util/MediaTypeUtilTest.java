/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class MediaTypeUtilTest {

    @Test
    void concrete() {
        assertEquals(Optional.of("a/b"),MediaTypeUtil.getMostSpecificMediaType("a/b", new HashSet<>(List.of("*/*","a/*","a/b","*/*"))));
    }

    @Test
    void matchType() {
        assertEquals(Optional.of("a/*"),MediaTypeUtil.getMostSpecificMediaType("a/b", new HashSet<>(List.of("*/*","a/*","*/*"))));
    }

    @Test
    void wildcard() {
        assertEquals(Optional.of("*/*"),MediaTypeUtil.getMostSpecificMediaType("a/b", new HashSet<>(List.of("b/*","*/*","b/a"))));
    }

    @Test
    void noMatch() {
        assertEquals(Optional.empty(),MediaTypeUtil.getMostSpecificMediaType("a/b", new HashSet<>(List.of("b/c","b/*","c/d"))));
    }

}