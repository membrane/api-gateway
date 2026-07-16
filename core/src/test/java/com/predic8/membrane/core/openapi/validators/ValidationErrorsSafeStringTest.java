/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
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

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the redaction contract of {@link ValidationErrors#toSafeString()} /
 * {@link ValidationContext#toSafeString(boolean)}: no value submitted by the client may leak into
 * the "safe" log line, while the structural metadata that identifies the failure is kept.
 */
class ValidationErrorsSafeStringTest {

    @Test
    void bodyValueIsNotLeaked() {
        ValidationContext ctx = ValidationContext.create()
                .method("POST").uriTemplate("/products")
                .schemaType("number").complexType("Product")
                .entityType(BODY).entity("REQUEST").statusCode(400)
                .addJSONpointerSegment("price");
        ValidationErrors errors = new ValidationErrors();
        errors.add(ctx, "-3 is smaller than the minimum of 0");

        String safe = errors.toSafeString();

        // The offending value must not appear ...
        assertFalse(safe.contains("-3"), safe);
        // ... but the structural information that locates the failure must.
        assertTrue(safe.contains("method=POST"), safe);
        assertTrue(safe.contains("uriTemplate=/products"), safe);
        assertTrue(safe.contains("xpointer=/price"), safe);
        assertTrue(safe.contains("statusCode=400"), safe);
        // "REQUEST" is an internal body-root sentinel, not a real field name.
        assertFalse(safe.contains("REQUEST"), safe);
    }

    @Test
    void concretePathIsNotLeaked() {
        // Invalid-path case (OpenAPIValidator): validatedEntity holds the concrete request path,
        // which can embed a path-parameter value like an e-mail address.
        ValidationContext ctx = ValidationContext.create()
                .method("GET")
                .entity("/users/jane.doe@example.com").entityType(PATH).statusCode(404);
        ValidationErrors errors = new ValidationErrors();
        errors.add(ctx, "Path /users/jane.doe@example.com is invalid.");

        String safe = errors.toSafeString();

        assertFalse(safe.contains("jane.doe@example.com"), safe);
        assertFalse(safe.contains("/users/"), safe);
        assertTrue(safe.contains("validatedEntityType=PATH"), safe);
        assertTrue(safe.contains("statusCode=404"), safe);
    }

    @Test
    void safeParameterNameIsKept() {
        // For a query parameter, validatedEntity/parameter are the schema-defined *name*, not a
        // submitted value, so they are safe to keep.
        ValidationContext ctx = ValidationContext.create()
                .method("GET").uriTemplate("/products")
                .parameter("color").entity("color").entityType(QUERY_PARAMETER).statusCode(400);
        ValidationErrors errors = new ValidationErrors();
        errors.add(ctx, "value 'ff00ff' does not match pattern");

        String safe = errors.toSafeString();

        assertFalse(safe.contains("ff00ff"), safe);
        assertTrue(safe.contains("parameter=color"), safe);
    }

    @Test
    void requestInfoIsLoggedOncePerRun() {
        ValidationContext base = ValidationContext.create()
                .method("POST").uriTemplate("/products").entityType(BODY).entity("REQUEST").statusCode(400);
        ValidationErrors errors = new ValidationErrors();
        errors.add(base.addJSONpointerSegment("price"), "-3 is smaller than the minimum of 0");
        errors.add(base.addJSONpointerSegment("name"), "must be at least 2 characters");

        String safe = errors.toSafeString();

        // method / uriTemplate belong to the whole run and must be printed exactly once.
        assertEquals(1, countOccurrences(safe, "method=POST"), safe);
        assertEquals(1, countOccurrences(safe, "uriTemplate=/products"), safe);
        // Both field locations are still present.
        assertTrue(safe.contains("xpointer=/price"), safe);
        assertTrue(safe.contains("xpointer=/name"), safe);
    }

    @Test
    void emptyErrorsProduceEmptyList() {
        assertEquals("[]", new ValidationErrors().toSafeString());
    }

    @Test
    void includeRequestInfoFlagTogglesMethodAndTemplate() {
        ValidationContext ctx = ValidationContext.create()
                .method("POST").uriTemplate("/products").entityType(BODY).statusCode(400);

        assertTrue(ctx.toSafeString(true).contains("method=POST"));
        assertFalse(ctx.toSafeString(false).contains("method=POST"));
        assertFalse(ctx.toSafeString(false).contains("uriTemplate="));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length()))
            count++;
        return count;
    }
}
