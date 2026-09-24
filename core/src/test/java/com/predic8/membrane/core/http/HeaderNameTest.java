/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderNameTest {

    final HeaderName hn = new HeaderName("Test-Header");

    @Test
    void testEquals() {
        assertEquals(hn, new HeaderName("TEST-HEADER"));
    }

    @Test
    void hasName() {
        assertTrue(hn.hasName("test-header"));
    }

    @Test
    void testHashCode() {
        assertEquals(hn.hashCode(), new HeaderName("test-Header").hashCode());
    }

    /**
     * equalsIgnoreCase() is locale independent, toLowerCase() is not: under tr-TR "X-ID"
     * lowercases to "x-ıd" (dotless i), so equal names would hash differently.
     */
    @Test
    @ResourceLock(Resources.LOCALE)
    void hashCodeIsLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));
            assertEquals(new HeaderName("X-ID").hashCode(), new HeaderName("x-id").hashCode());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void testToString() {
        assertEquals("Test-Header", hn.toString());
    }
}
