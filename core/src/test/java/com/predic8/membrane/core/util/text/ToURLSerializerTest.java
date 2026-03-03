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

package com.predic8.membrane.core.util.text;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.text.ToURLSerializer.toURL;
import static org.junit.jupiter.api.Assertions.*;

class ToURLSerializerTest {

    @Test
    void nullInput() {
        assertEquals("null", toURL(null));
    }

    @Test
    void encodesPlainString() {
        assertEquals("hello+world", toURL("hello world"));
    }

    @Test
    void encodesSpecialCharacters() {
        assertEquals("%26%3F%C3%A4%C3%B6%C3%BC%21%22%3D%3A%23%2F%5C", toURL("&?äöü!\"=:#/\\"));
    }

    @Test
    void encodesUnicode() {
        assertEquals("M%C3%BCller", toURL("Müller"));
    }

    @Test
    void encodesNumberViaTextSerialization() {
        assertEquals("123", toURL(123));
    }

    @Test
    void encodesBooleanViaTextSerialization() {
        assertEquals("true", toURL(true));
    }
}