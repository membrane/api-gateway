/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.StringUtil.truncateAfter;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {

    private static final String POEM = "To the greene forest so pleasant and faire";

    @Test
    void truncateAfterTest() {
        assertEquals("", truncateAfter(POEM,0));
        assertEquals("To the greene", truncateAfter(POEM,13));
        assertEquals(POEM, truncateAfter(POEM,POEM.length()));
        assertEquals(POEM, truncateAfter(POEM,1000));
    }
}