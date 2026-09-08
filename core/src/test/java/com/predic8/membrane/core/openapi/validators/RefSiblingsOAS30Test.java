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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.openapi.model.Request.post;
import static com.predic8.membrane.core.openapi.util.JsonTestUtil.mapToJson;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OAS 3.0 ignores keywords sitting next to a $ref, so discarding them is the specified
 * behaviour here. This is the control group for {@link RefSiblingsOAS31Test} (#3188): a fix for
 * 3.1 must not change any of these results.
 */
class RefSiblingsOAS30Test extends AbstractValidatorTest {

    private static final Map<String, Object> ADDRESS = Map.of("street", "Koblenzer Str. 65", "city", "Bonn");

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/ref-siblings.yaml";
    }

    @Test
    void refIsResolvedAndApplied() {
        assertEquals(List.of(), messages(validate("nullableRef", ADDRESS)));

        ValidationError error = single(validate("nullableRef", Map.of()));
        assertEquals("Required properties city,street are missing in object /nullableRef.", error.getMessage());
        assertEquals("REQUEST/BODY#/nullableRef", error.getContext().getLocationForRequest());
        assertEquals("Address", error.getContext().getComplexType());
        assertEquals(BODY, error.getContext().getValidatedEntityType());
        assertEquals(400, error.getContext().getStatusCode());
    }

    @Test
    void siblingNullableIsIgnoredAsSpecified() {
        ValidationError error = single(validate("nullableRef", null));
        assertEquals("Value null is not an object.", error.getMessage());
        assertEquals("REQUEST/BODY#/nullableRef", error.getContext().getLocationForRequest());
        assertEquals("object", error.getContext().getSchemaType());
    }

    @Test
    void siblingReadOnlyIsIgnoredAsSpecified() {
        assertEquals(List.of(), messages(validate("readOnlyRef", ADDRESS)));
    }

    @Test
    void siblingRequiredIsIgnoredAsSpecified() {
        // "zip" is required next to the $ref only, so it is not demanded ...
        assertEquals(List.of(), messages(validate("extraRequiredRef", ADDRESS)));

        // ... while "city" from the referenced schema still is
        ValidationError error = single(validate("extraRequiredRef", Map.of("street", "Koblenzer Str. 65")));
        assertEquals("Required property city is missing.", error.getMessage());
        assertEquals("REQUEST/BODY#/extraRequiredRef/city", error.getContext().getLocationForRequest());
    }

    @Test
    void siblingTypeIsIgnoredAsSpecified() {
        // AnyValue has no type and the sibling `type: string` is dropped, so anything goes
        assertEquals(List.of(), messages(validate("narrowedRef", 42)));
    }

    private ValidationErrors validate(String property, Object value) {
        Map<String, Object> body = new HashMap<>();
        body.put(property, value);
        return validator.validate(post().path("/siblings").body(mapToJson(body)));
    }

    private static ValidationError single(ValidationErrors errors) {
        assertEquals(1, errors.size(), () -> "Expected exactly one error but got " + messages(errors));
        return errors.getFirst();
    }

    private static List<String> messages(ValidationErrors errors) {
        return errors.getErrors().stream().map(ValidationError::getMessage).toList();
    }
}
