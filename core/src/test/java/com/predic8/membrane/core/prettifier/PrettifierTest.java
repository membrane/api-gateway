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

package com.predic8.membrane.core.prettifier;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.prettifier.Prettifier.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PrettifierTest {

    @Test
    void json() {
        assertEquals(JSON, getInstance(APPLICATION_JSON));
        assertEquals(JSON, getInstance(APPLICATION_JSON_UTF8));
        assertEquals(JSON, getInstance(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void xml() {
        assertEquals(XML, getInstance(APPLICATION_XML));
        assertEquals(XML, getInstance(TEXT_XML));
        assertEquals(XML, getInstance(TEXT_XML_UTF8));
    }

    @Test
    void text() {
        assertEquals(TEXT, getInstance(TEXT_PLAIN));
        assertEquals(TEXT, getInstance(TEXT_PLAIN_UTF8));
        assertEquals(TEXT, getInstance("trash"));
    }

}