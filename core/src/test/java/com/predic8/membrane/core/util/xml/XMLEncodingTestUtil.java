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

package com.predic8.membrane.core.util.xml;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class XMLEncodingTestUtil {

    public static final String UMLAUTE = "Umlaute: ä ö ü Ä Ö Ü ß";
    public static final String ACCENTS = "Accents: é è ê à ç ô ï";
    public static final String ESPANOL = "Español: niño, corazón";
    public static final String NORDIC = "Nordic: å æ ø Å Æ Ø";
    public static final String ENTITIES = "&lt; > &amp; \" '";

    public static void assertChars(String actual) {
        for (String expected : List.of(UMLAUTE, ACCENTS, ESPANOL, NORDIC, ENTITIES)) {
            assertTrue(actual.contains(expected), "Missing expected substring: %s Actual: %s\n".formatted(expected, actual));
        }
    }
}
