/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.headerfilter;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.Action.KEEP;
import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterRule.*;
import static org.junit.jupiter.api.Assertions.*;

class HeaderFilterRuleTest {

    @Test
    void matchAll() {
        assertTrue(  remove(".*").matches("Foo"));
    }

    @Test
    void matchPattern() {
        var hfr = HeaderFilterRule.keep("X-[F|Z]oo");
        assertTrue(hfr.matches("X-Foo"));
        assertTrue(hfr.matches("X-Zoo"));
        assertFalse(hfr.matches("X-Boo"));
        assertEquals(KEEP, hfr.getAction());
    }

    @Test
    void caseInsensitive() {
        var hfr = HeaderFilterRule.keep("x-bar.*");
        assertTrue(hfr.matches("x-bar"));
        assertTrue(hfr.matches("X-Bar"));
        assertTrue(hfr.matches("X-BARRRR"));
        assertTrue(hfr.matches("x-Barrrrr"));
    }

}