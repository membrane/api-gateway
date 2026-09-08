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

package com.predic8.membrane.core.interceptor.json;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.json.JsonLimits.UNLIMITED;
import static org.junit.jupiter.api.Assertions.*;

class JsonLimitsTest {

    private static JsonLimits withMaxSize(int maxSize) {
        return new JsonLimits(4096, maxSize, 10, 20, 10, 10, 2048, true);
    }

    @Test
    void everyNegativeMaxSizeMeansUnlimited() {
        assertEquals(UNLIMITED, withMaxSize(-1).maxSize());
        assertEquals(UNLIMITED, withMaxSize(-5).maxSize());
    }

    @Test
    void unlimitedIsNeverExceeded() {
        assertFalse(withMaxSize(UNLIMITED).exceedsMaxSize(Long.MAX_VALUE));
    }

    @Test
    void aLimitIsExceededOnlyAboveIt() {
        assertFalse(withMaxSize(100).exceedsMaxSize(100));
        assertTrue(withMaxSize(100).exceedsMaxSize(101));
    }

    @Test
    void unlimitedBecomesTheHighestCeiling() {
        assertEquals(Integer.MAX_VALUE, withMaxSize(UNLIMITED).sizeCeiling());
        assertEquals(100, withMaxSize(100).sizeCeiling());
    }
}
