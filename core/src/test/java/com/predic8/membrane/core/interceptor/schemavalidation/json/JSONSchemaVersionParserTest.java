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

package com.predic8.membrane.core.interceptor.schemavalidation.json;

import com.networknt.schema.SpecificationVersion;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.networknt.schema.SpecificationVersion.*;
import static com.predic8.membrane.core.interceptor.schemavalidation.json.JSONSchemaVersionParser.*;
import static org.junit.jupiter.api.Assertions.*;

class JSONSchemaVersionParserTest {

    @Test
    void parseUnknownVersion() {
        assertThrows(ConfigurationException.class, () -> parse("invalid-version"));
    }

    @Test
    void parseNullVersion() {
        assertThrows(ConfigurationException.class, () -> parse(null));
    }

    @Test
    void parseFromAlias() {
        assertEquals(DRAFT_4, parse("04"));
        assertEquals(DRAFT_6, parse("06"));
        assertEquals(DRAFT_7, parse("07"));
        assertEquals(DRAFT_4, parse("draft-04"));
        assertEquals(DRAFT_6, parse("draft-06"));
        assertEquals(DRAFT_7, parse("draft-07"));
        assertEquals(DRAFT_2019_09, parse("2019-09"));
        assertEquals(DRAFT_2020_12, parse("2020-12"));
    }
}