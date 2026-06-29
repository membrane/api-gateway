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

package com.predic8.membrane.annot.yaml.error;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.annot.yaml.error.LineYamlErrorRenderer.getLastSegment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineYamlErrorRendererTest {

    @Test
    void splitsJsonPathSegments() {
        assertPath("$.foo.bar", "$.foo", "bar");
        assertPath("$.foo['bar.baz']", "$.foo", "bar.baz");
        assertPath("$.foo['bar.baz'].qux", "$.foo['bar.baz']", "qux");
        assertPath("$.foo['bar.baz'][0]", "$.foo['bar.baz']", "0");
        assertPath("$.foo[12]", "$.foo", "12");
        assertPath("$.foo[12].bar", "$.foo[12]", "bar");
        assertPath("$.foo[12]['bar.baz']", "$.foo[12]", "bar.baz");
        assertPath("$['foo.bar']", "$", "foo.bar");
        assertPath("$.foo['bar\\'baz']", "$.foo", "bar'baz");
        assertPath("$.foo['bar\\\\baz']", "$.foo", "bar\\baz");
    }

    @Test
    void rejectsJsonPathWithoutSegment() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> getLastSegment("$")
        );

        assertEquals("Cannot determine parent path of: $", e.getMessage());
    }

    private static void assertPath(String jsonPath, String expectedParentPath, String expectedLastSegment) {
        assertEquals(expectedLastSegment, getLastSegment(jsonPath));
        assertEquals(expectedParentPath, LineYamlErrorRenderer.getParentPath(jsonPath));
    }
}
