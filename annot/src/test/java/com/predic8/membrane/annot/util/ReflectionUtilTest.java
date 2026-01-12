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

package com.predic8.membrane.annot.util;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionUtilTest {


    @Test
    void convertString() {
        var o = ReflectionUtil.convert("abc", String.class);
        assertInstanceOf(String.class, o);
        assertEquals("abc", o);
    }

    @Test
    void convertInteger() {
        var o = ReflectionUtil.convert("123", Integer.class);
        assertInstanceOf(Integer.class, o);
        assertEquals(123, o);
    }

    @Test
    void convertBoolean() {
        var o = ReflectionUtil.convert("true", Boolean.class);
        assertInstanceOf(Boolean.class, o);
        assertEquals(true, o);
    }

    @Test
    void convertNull() {
        var o = ReflectionUtil.convert(null, String.class);
        assertNull(o);
    }

    @Test
    void convertInvalid() {
        assertThrows(RuntimeException.class, () -> ReflectionUtil.convert("abc", Integer.class));
    }
}