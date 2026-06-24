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

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.openapi.model.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A schema with {@code additionalProperties: false} and no declared properties is closed, so any
 * named part must be rejected as unexpected.
 */
public class MultipartClosedNoPropertiesTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart/closed-no-properties.oas.yaml";
    }

    @Test
    void unexpectedPartIsReported() throws ParseException {
        var body = new MultipartBuilder()
                .part("metadata", null, APPLICATION_JSON, null, """
                        {"id": 1}""")
                .build();

        var errors = validator.validate(post().path("/upload")
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(body));

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("unexpected"), () -> "Errors: " + errors);
    }
}
