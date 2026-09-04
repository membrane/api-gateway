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

import static com.predic8.membrane.core.interceptor.xmlprotection.XMLLimits.*;
import static org.junit.jupiter.api.Assertions.*;

class XMLLimitsTest {

    @Test
    void everyNegativeLimitMeansUnlimited() {
        XMLLimits limits = new XMLLimits(-5, -3, -2, -7, UNLIMITED, true);

        assertEquals(UNLIMITED, limits.maxElementNameLength());
        assertEquals(UNLIMITED, limits.maxAttributeNameLength());
        assertEquals(UNLIMITED, limits.maxAttributeCount());
        assertEquals(UNLIMITED, limits.maxDepth());
    }

    @Test
    void jaxpNameLimitClearsTheLargestNameLimitByTheFullHeadroom() {
        // A cap just above the limit would preempt our own check for every name more than one
        // character too long, turning a named violation back into "not well-formed"
        assertEquals(2000 + JAXP_NAME_HEADROOM, new XMLLimits(1000, 2000, 1000, 50, UNLIMITED, true).jaxpNameLimit());
        assertEquals(2000 + JAXP_NAME_HEADROOM, new XMLLimits(2000, 1000, 1000, 50, UNLIMITED, true).jaxpNameLimit());
    }

    @Test
    void anUnlimitedNameLengthLeavesTheJaxpCapOpen() {
        assertEquals(0, new XMLLimits(UNLIMITED, 1000, 1000, 50, UNLIMITED, true).jaxpNameLimit());
        assertEquals(0, new XMLLimits(1000, UNLIMITED, 1000, 50, UNLIMITED, true).jaxpNameLimit());
    }

    @Test
    void aMaximalNameLengthDoesNotOverflowTheJaxpCap() {
        assertEquals(0, new XMLLimits(Integer.MAX_VALUE, 1000, 1000, 50, UNLIMITED, true).jaxpNameLimit());
    }

    @Test
    void unlimitedIsNeverExceeded() {
        assertFalse(exceeds(Integer.MAX_VALUE, UNLIMITED));
    }

    @Test
    void limitIsExceededOnlyAboveIt() {
        assertFalse(exceeds(2, 3));
        assertFalse(exceeds(3, 3));
        assertTrue(exceeds(4, 3));
    }
}
