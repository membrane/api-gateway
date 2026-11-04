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

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class StringValidatorTest {

    @Test
    void patternSkippedWhenMaxLengthExceeded() {
        final var errors = validate(new Schema<>().type("string").maxLength(3).pattern("^[0-9]+$"), "abcd");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("MaxLength"));
    }

    @Test
    void invalidPatternSkippedWhenMaxLengthExceeded() {
        final var errors = validate(new Schema<>().type("string").maxLength(3).pattern("[invalid("), "abcd");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("MaxLength"));
    }

    @Test
    void patternCheckedWithinMaxLength() {
        final var errors = validate(new Schema<>().type("string").maxLength(5).pattern("^[0-9]+$"), "abc");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("does not match regex pattern"));
    }

    @Test
    void patternCheckedWithoutMaxLength() {
        final var schema = new Schema<>().type("string").pattern("^[0-9]+$");
        assertEquals(1, validate(schema, "abc").size());
        assertTrue(validate(schema, "123").isEmpty());
    }

    @Test
    void invalidPatternStillFails() {
        final var errors = validate(new Schema<>().type("string").pattern("[invalid("), "abc");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("is not of string format"));
    }

    private static ValidationErrors validate(Schema<?> schema, String value) {
        return new SchemaValidator(new OpenAPI(), schema).validate(new ValidationContext(), value);
    }
}
