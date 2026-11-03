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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class NumberRestrictionValidatorTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * Numeric keywords apply only to numbers, so a string of digits is not compared with them.
     */
    @Test
    void stringIsNotCheckedAgainstNumericKeywords() {
        assertTrue(validate(new Schema<>().maximum(new BigDecimal(5)), NODES.textNode("7")).isEmpty());
    }

    @Test
    void numberIsCheckedAgainstNumericKeywords() {
        assertEquals(1, validate(new Schema<>().maximum(new BigDecimal(5)), NODES.numberNode(7)).size());
    }

    /**
     * Parsing a long string of digits as a number takes quadratic time, about 10 s for a million digits.
     */
    @Test
    void longDigitStringIsNotParsed() {
        final var digits = NODES.textNode("1".repeat(1_000_000));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertTrue(validate(new Schema<>().maximum(new BigDecimal(5)), digits).isEmpty());
            assertTrue(validate(new StringSchema(), digits).isEmpty());
        });
    }

    @Test
    void longDigitStringInJsonBodyIsValidatedQuickly() {
        final var schema = new ObjectSchema().addProperty("name", new StringSchema());
        final var body = NODES.objectNode().put("name", "1".repeat(1_000_000));
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertTrue(new SchemaValidator(new OpenAPI(), schema).validate(new ValidationContext(), body).isEmpty()));
    }

    private static ValidationErrors validate(Schema<?> schema, Object value) {
        return new ValidationErrors().add(new NumberRestrictionValidator(schema).validate(new ValidationContext(), value));
    }
}
