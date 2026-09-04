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

package com.predic8.membrane.core.interceptor.xmlprotection;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.xmlprotection.DoctypeInspector.getHeaderAfterRootName;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DoctypeInspectorTest {

    @Test
    void getHeaderAfterRootName_stripsSimpleRootName() {
        assertEquals(" SYSTEM 'x.dtd'", getHeaderAfterRootName("<!DOCTYPE r SYSTEM 'x.dtd'"));
    }

    @Test
    void getHeaderAfterRootName_stripsRootNameNamedSystem() {
        // The keyword remaining after the root name is skipped must not be "SYSTEM" itself
        assertEquals(" []", getHeaderAfterRootName("<!DOCTYPE SYSTEM []"));
    }

    @Test
    void getHeaderAfterRootName_stripsRootNameNamedPublic() {
        assertEquals(" []", getHeaderAfterRootName("<!DOCTYPE PUBLIC []"));
    }

    @Test
    void getHeaderAfterRootName_stripsRootNameContainingKeywordSubstring() {
        assertEquals(" ", getHeaderAfterRootName("<!DOCTYPE PUBLICATIONS "));
    }

    @Test
    void getHeaderAfterRootName_skipsExtraWhitespaceBeforeRootName() {
        assertEquals("   SYSTEM 'x'", getHeaderAfterRootName("<!DOCTYPE   r   SYSTEM 'x'"));
    }

    @Test
    void getHeaderAfterRootName_handlesRootNameWithNoTrailingContent() {
        assertEquals("", getHeaderAfterRootName("<!DOCTYPE r"));
    }

    @Test
    void getHeaderAfterRootName_handlesMissingDoctypeKeywordDefensively() {
        // Should never happen per the DTD contract, but must not throw — falls back to
        // skipping the header's first whitespace-delimited token.
        assertEquals(" bar baz", getHeaderAfterRootName("foo bar baz"));
    }

    @Test
    void getHeaderAfterRootName_handlesEmptyHeader() {
        assertEquals("", getHeaderAfterRootName(""));
    }
}
