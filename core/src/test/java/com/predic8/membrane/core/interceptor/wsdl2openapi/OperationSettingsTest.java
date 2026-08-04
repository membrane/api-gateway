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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationSettingsTest {

    @Test
    void pathWithLeadingSlashIsNormalized() {
        var s = new OperationSettings();
        s.setPath("/foo");
        assertEquals("foo", s.getPath());
    }

    @Test
    void pathWithoutLeadingSlashIsUnchanged() {
        var s = new OperationSettings();
        s.setPath("foo");
        assertEquals("foo", s.getPath());
    }

    @Test
    void nullPathIsPreserved() {
        var s = new OperationSettings();
        s.setPath(null);
        assertNull(s.getPath());
    }
}
