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

package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.util.URIFactory;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.model.Request.post;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.parseOpenAPI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpenAPIValidatorXMLTest {

    OpenAPIValidator validator;
    OpenAPIValidator validatorRequired;

    /** A valid order with all fields present and correct types. */
    final String validOrder = """
            <order>
              <id>4711</id>
              <customer>Anna Müller</customer>
              <items>
                <item>
                  <productId>P10</productId>
                  <quantity>2</quantity>
                </item>
                <item>
                  <productId>P11</productId>
                  <quantity>1</quantity>
                </item>
              </items>
            </order>
            """;

    /** Order with no items array — still valid against the base spec (items is optional). */
    final String orderNoItems = """
            <order>
              <id>4711</id>
              <customer>Anna Müller</customer>
            </order>
            """;

    /** Order where <id> contains a non-integer string. */
    final String orderBadIdType = """
            <order>
              <id>not-a-number</id>
              <customer>Anna Müller</customer>
            </order>
            """;

    /** Order missing the required <customer> element. */
    final String orderMissingCustomer = """
            <order>
              <id>4711</id>
            </order>
            """;

    @BeforeEach
    void setUp() {
        validator = new OpenAPIValidator(new URIFactory(),
                new OpenAPIRecord(
                        parseOpenAPI(getResourceAsStream(this, "/openapi/specs/xml/xml-message.oas.yaml")),
                        new OpenAPISpec()));

        validatorRequired = new OpenAPIValidator(new URIFactory(),
                new OpenAPIRecord(
                        parseOpenAPI(getResourceAsStream(this, "/openapi/specs/xml/xml-message-required.oas.yaml")),
                        new OpenAPISpec()));
    }

    // -----------------------------------------------------------------------
    // Valid requests
    // -----------------------------------------------------------------------

    @Test
    void validOrderShouldPassWithNoErrors() throws ParseException {
        var errors = validator.validate(post().path("/orders").xml(validOrder));
        assertEquals(0, errors.size(), "Expected no validation errors: " + errors);
    }

    @Test
    void validOrderWithoutItemsIsAccepted() throws ParseException {
        var errors = validator.validate(post().path("/orders").xml(orderNoItems));
        assertEquals(0, errors.size(), "Expected no validation errors: " + errors);
    }

    // -----------------------------------------------------------------------
    // Type errors
    // -----------------------------------------------------------------------

    @Test
    void nonIntegerIdProducesValidationError() throws ParseException {
        var errors = validator.validate(post().path("/orders").xml(orderBadIdType));
        assertFalse(errors.isEmpty(), "Expected a type validation error for non-integer id");
    }

    // -----------------------------------------------------------------------
    // Required field violations
    // -----------------------------------------------------------------------

    @Test
    void missingRequiredFieldProducesError() throws ParseException {
        var errors = validatorRequired.validate(post().path("/orders").xml(orderMissingCustomer));
        assertFalse(errors.isEmpty(), "Expected a validation error for missing required field 'customer'");
    }

    @Test
    void allRequiredFieldsPresentShouldPass() throws ParseException {
        var errors = validatorRequired.validate(post().path("/orders").xml(validOrder));
        assertEquals(0, errors.size(), "Expected no validation errors: " + errors);
    }

    // -----------------------------------------------------------------------
    // Malformed XML
    // -----------------------------------------------------------------------

    @Test
    void malformedXmlProducesParseError() throws ParseException {
        var errors = validator.validate(post().path("/orders").xml("<order><unclosed>"));
        assertFalse(errors.isEmpty(), "Expected a parse error for malformed XML");
    }

    /** Text interleaved with child elements cannot be mapped onto the schema and is rejected. */
    @Test
    void mixedContentProducesValidationError() throws ParseException {
        String mixed = """
                <order>
                  <id>4711</id>
                  <customer>Anna <b>Müller</b></customer>
                </order>
                """;
        var errors = validator.validate(post().path("/orders").xml(mixed));
        assertFalse(errors.isEmpty(), "Expected a validation error for mixed content");
    }

    /** A single-valued element occurring multiple times is rejected instead of dropping extras. */
    @Test
    void repeatedSingleValuedElementProducesValidationError() throws ParseException {
        String duplicate = """
                <order>
                  <id>4711</id>
                  <id>4712</id>
                  <customer>Anna Müller</customer>
                </order>
                """;
        var errors = validator.validate(post().path("/orders").xml(duplicate));
        assertFalse(errors.isEmpty(), "Expected a validation error for a repeated single-valued element");
    }
}
