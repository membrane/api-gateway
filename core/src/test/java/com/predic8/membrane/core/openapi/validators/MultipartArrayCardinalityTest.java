/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.validators;

import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static com.predic8.membrane.core.openapi.model.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An array property is sent as one part per item. The array-level cardinality (minItems/maxItems)
 * must be enforced against the number of occurrences, not only the per-item schema.
 */
public class MultipartArrayCardinalityTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart/array-cardinality.oas.yaml";
    }

    @Test
    void countWithinBoundsPasses() throws ParseException {
        var errors = validateUpload(new MultipartBuilder()
                .part("tags", null, TEXT_PLAIN, null, "a")
                .part("tags", null, TEXT_PLAIN, null, "b")
                .build());

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void tooFewItemsViolatesMinItems() throws ParseException {
        var errors = validateUpload(new MultipartBuilder()
                .part("tags", null, TEXT_PLAIN, null, "a")
                .build());

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("minitems"), () -> "Errors: " + errors);
    }

    @Test
    void tooManyItemsViolatesMaxItems() throws ParseException {
        var errors = validateUpload(new MultipartBuilder()
                .part("tags", null, TEXT_PLAIN, null, "a")
                .part("tags", null, TEXT_PLAIN, null, "b")
                .part("tags", null, TEXT_PLAIN, null, "c")
                .part("tags", null, TEXT_PLAIN, null, "d")
                .build());

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("maxitems"), () -> "Errors: " + errors);
    }

    private ValidationErrors validateUpload(String body) throws ParseException {
        return validator.validate(post().path("/upload")
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(body));
    }
}
