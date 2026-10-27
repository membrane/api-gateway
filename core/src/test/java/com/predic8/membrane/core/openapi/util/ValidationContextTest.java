/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.util;

import com.predic8.membrane.core.openapi.validators.ValidationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidationContextTest {

    @Test
    public void getLocation() {
        Assertions.assertEquals("REQUEST/BODY#/only-numbers/1",
                new ValidationContext().path("/array")
                        .complexType("Array")
                        .entityType(BODY)
                        .entity("REQUEST")
                        .addJSONpointerSegment("only-numbers")
                        .addJSONpointerSegment("1")
                        .getLocationForRequest());
    }

    /**
     * Segments are escaped as defined by RFC 6901 and keep their boundaries. See #3568.
     */
    @Test
    void addJSONpointerSegmentKeepsSegmentBoundaries() {
        assertEquals("/a~1b/c~0d/0",
                new ValidationContext()
                        .addJSONpointerSegment("a/b")
                        .addJSONpointerSegment("c~d")
                        .addJSONpointerSegment("0")
                        .getJSONpointer());
    }
}
