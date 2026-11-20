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

package com.predic8.membrane.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.SystemPropertyUtil.getPositiveIntProperty;
import static org.junit.jupiter.api.Assertions.*;

class SystemPropertyUtilTest {

    private static final String PROPERTY = "membrane.test.systempropertyutil";

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void missingPropertyGivesTheDefault() {
        assertEquals(8192, getPositiveIntProperty(PROPERTY, 8192));
    }

    @Test
    void positiveValueIsUsed() {
        System.setProperty(PROPERTY, "1");
        assertEquals(1, getPositiveIntProperty(PROPERTY, 8192));
    }

    @Test
    void zeroIsRejectedNamingTheProperty() {
        assertRejected("0");
    }

    @Test
    void negativeValueIsRejected() {
        assertRejected("-1");
    }

    @Test
    void valueThatIsNotANumberIsRejected() {
        final var e = assertRejected("8k");
        assertInstanceOf(NumberFormatException.class, e.getCause());
    }

    private static IllegalArgumentException assertRejected(String value) {
        System.setProperty(PROPERTY, value);
        final var e = assertThrows(IllegalArgumentException.class, () -> getPositiveIntProperty(PROPERTY, 8192));
        assertEquals("System property %s must be a positive integer, but is '%s'.".formatted(PROPERTY, value), e.getMessage());
        return e;
    }
}
