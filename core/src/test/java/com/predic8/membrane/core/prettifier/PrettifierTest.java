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
import static org.junit.jupiter.api.Assertions.assertSame;

class PrettifierTest {

    @Test
    void json() {
        assertSame(JSONPrettifier.INSTANCE, getInstance(APPLICATION_JSON));
        assertSame(JSONPrettifier.INSTANCE, getInstance(APPLICATION_JSON_UTF8));
        assertSame(JSONPrettifier.INSTANCE, getInstance(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void xml() {
        assertSame(XMLPrettifier.INSTANCE, getInstance(APPLICATION_XML));
        assertSame(XMLPrettifier.INSTANCE, getInstance(APPLICATION_PROBLEM_XML));
        assertSame(XMLPrettifier.INSTANCE, getInstance(TEXT_XML));
        assertSame(XMLPrettifier.INSTANCE, getInstance(TEXT_XML_UTF8));
    }

    @Test
    void text() {
        assertSame(TextPrettifier.INSTANCE, getInstance(TEXT_PLAIN));
        assertSame(TextPrettifier.INSTANCE, getInstance(TEXT_PLAIN_UTF8));
    }

    @Test
    void unknown() {
        assertSame(NullPrettifier.INSTANCE, getInstance("unknown"));
        assertSame(NullPrettifier.INSTANCE, getInstance(null));
    }

}