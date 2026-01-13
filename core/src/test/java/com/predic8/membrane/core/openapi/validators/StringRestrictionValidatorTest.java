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

package com.predic8.membrane.core.openapi.validators;

import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

class StringRestrictionValidatorTest {

    ValidationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ValidationContext();
    }

    @Test
    void maxLength() {
        var validator = new StringRestrictionValidator(new Schema<>().type("string").maxLength(3));
        run(c -> {
            assertEquals(0, validator.validate(c, "123").size());
            assertEquals(1, validator.validate(c, "1234").size());
        });
    }

    @Test
    void minLength() {
        var validator = new StringRestrictionValidator(new Schema<>().type("string").minLength(3));
        run(c -> {
            assertEquals(1, validator.validate(c, "12").size());
            assertEquals(0, validator.validate(c, "123").size());
        });
    }

    @Test
    void pattern() {
        var validator = new StringRestrictionValidator(
                new Schema<>().type("string").pattern("[a-z] [0-9]")
        );
        run(c -> {
            assertEquals(0, validator.validate(c, "a 1").size());
            assertEquals(1, validator.validate(c, "a b").size());
        });
    }

    @Test
    void combinedMinMax() {
        var validator = new StringRestrictionValidator(
                new Schema<>().type("string").minLength(2).maxLength(4)
        );
        run(c -> {
            assertEquals(1, validator.validate(c, "a").size());
            assertEquals(0, validator.validate(c, "ab").size());
            assertEquals(0, validator.validate(c, "abcd").size());
            assertEquals(1, validator.validate(c, "abcde").size());
        });
    }

    @Test
    void noRestrictions() {
        var validator = new StringRestrictionValidator(new Schema<>().type("string"));
        run(c -> {
            assertEquals(0, validator.validate(c, "").size());
            assertEquals(0, validator.validate(c, "anything").size());
        });
    }

    @Test
    void constValue() {
        var validator = new StringRestrictionValidator(
                new Schema<>().type("string")._const("abc")
        );
        run(c -> {
            assertEquals(0, validator.validate(c, "abc").size());
            assertEquals(1, validator.validate(c, "abcd").size());
            assertEquals(1, validator.validate(c, "ab").size());
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void enumValues() {
        var validator = new StringRestrictionValidator(
                new Schema<>().type("string")._enum(java.util.List.of("red", "green", "blue"))
        );
        run(c -> {
            assertEquals(0, validator.validate(c, "red").size());
            assertEquals(0, validator.validate(c, "green").size());
            assertEquals(1, validator.validate(c, "yellow").size());
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void nullValueIsIgnored() {
        var validator = new StringRestrictionValidator(
                new Schema<>().type("string")
                        .minLength(3)
                        .maxLength(5)
                        .pattern("[a-z]+")
                        ._const("abc")
                        ._enum(java.util.List.of("abc", "def"))
        );
        run(c -> assertNull(validator.validate(c, null)));
    }

    @Test
    void invalidPattern() {
        var validator = new StringRestrictionValidator(
                new Schema<>().type("string").pattern("[invalid(")
        );
        run(c -> {
            var err = validator.validate(c, "anything");
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("Invalid pattern"));
        });
    }

    private void run(java.util.function.Consumer<ValidationContext> test) {
        test.accept(ctx);

        // There are differences in Query Parameters
        test.accept(ctx.entityType(QUERY_PARAMETER));
    }
}
