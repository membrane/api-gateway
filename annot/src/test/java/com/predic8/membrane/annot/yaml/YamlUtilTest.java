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

package com.predic8.membrane.annot.yaml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class YamlUtilTest {

    @Test
    void removesOnlyFirstYamlDocStartMarker() {
        String input = """
            ---
            kind: api
            name: users
            ---
            port: 8080
            """;

        String expected = """
            kind: api
            name: users
            ---
            port: 8080
            """;

        String result = YamlUtil.removeFirstYamlDocStartMarker(input);

        assertEquals(expected, result);
    }

    @Test
    void leavesContentUntouchedIfNoMarkerPresent() {
        String input = """
            kind: api
            name: users
            port: 8080
            """;

        assertEquals(input, YamlUtil.removeFirstYamlDocStartMarker(input));
    }

    @Test
    void handlesLeadingSpacesBeforeMarker() {
        String input = """
              ---
            key: value
            """;

        assertEquals("key: value\n", YamlUtil.removeFirstYamlDocStartMarker(input));
    }

    @Test
    void returnsNullIfInputIsNull() {
        assertNull(YamlUtil.removeFirstYamlDocStartMarker(null));
    }
}
