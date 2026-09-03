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

import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.openapi.model.Request.post;
import static com.predic8.membrane.core.openapi.util.JsonTestUtil.mapToJson;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAS 3.1 follows JSON Schema 2020-12, where $ref is an ordinary applicator keyword: a sibling
 * keyword is applied alongside the schema the $ref points at, and the two are AND-ed. See #3188.
 */
class RefSiblingsOAS31Test extends AbstractValidatorTest {

    private static final Map<String, Object> ADDRESS = Map.of("street", "Koblenzer Str. 65", "city", "Bonn");

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/ref-siblings.yaml";
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

    /**
     * The siblings survive parsing, so whatever is lost is lost inside Membrane, not upstream.
     */
    @Test
    void parserKeepsSiblingsNextToRef() {
        Map<String, Schema> properties = validator.getApi().getPaths().get("/siblings").getPost()
                .getRequestBody().getContent().get("application/json").getSchema().getProperties();

        Schema<?> nullableRef = properties.get("nullableRef");
        assertEquals("#/components/schemas/Address", nullableRef.get$ref());
        assertEquals(List.of("object", "null"), List.copyOf(nullableRef.getTypes()));

        assertTrue(properties.get("readOnlyRef").getReadOnly());
        assertEquals(List.of("zip"), properties.get("extraRequiredRef").getRequired());
    }

    /**
     * $ref and its siblings are AND-ed, so the target keeps applying: Address is `type: object`
     * and rejects null even though the sibling allows it. The spec-correct way to spell a
     * nullable reference is `anyOf: [{$ref: Address}, {type: "null"}]`.
     */
    @Test
    void siblingNullTypeDoesNotOverrideRefTarget() {
        ValidationError error = single(validate("nullableRef", null));
        assertEquals("null is of type null which does not match any of [object]", error.getMessage());
        assertEquals("REQUEST/BODY#/nullableRef", error.getContext().getLocationForRequest());
    }

    @Test
    void siblingTypeNarrowsUntypedRef() {
        // AnyValue has no type, so the sibling `type: string` is the only constraint
        assertEquals(List.of(), messages(validate("narrowedRef", "Bonn")));

        ValidationError error = single(validate("narrowedRef", 42));
        assertEquals("42 is of type integer which does not match any of [string]", error.getMessage());
        assertEquals("REQUEST/BODY#/narrowedRef", error.getContext().getLocationForRequest());
    }

    /**
     * Both the target and the sibling reject the value, and both errors are reported. That is what
     * the union of two applied schemas amounts to.
     */
    @Test
    void siblingAndTargetBothReportTypeMismatch() {
        assertEquals(List.of("\"foo\" is of type string which does not match any of [object]",
                        "\"foo\" is of type string which does not match any of [object, null]"),
                messages(validate("nullableRef", "foo")));
    }

    @Test
    void siblingRequiredIsApplied() {
        // "zip" is required next to the $ref, the referenced Address has no such requirement
        ValidationErrors errors = validate("extraRequiredRef", ADDRESS);

        assertEquals(List.of("Required property zip is missing."), messages(errors));
        assertEquals("REQUEST/BODY#/extraRequiredRef/zip", errors.getFirst().getContext().getLocationForRequest());
    }

    /**
     * Both schemas require "city", so both report it missing. The user gets one error, not two.
     */
    @Test
    void repeatedAssertionIsReportedOnce() {
        assertEquals(List.of("Required property city is missing."),
                messages(validate("duplicateRequiredRef", Map.of("street", "Koblenzer Str. 65"))));
    }

    /**
     * Node.next points back at Node, so both the referenced schema and the keywords next to that
     * $ref apply again at every nested node. The recursion follows the instance, it does not stop
     * at the first repetition of the schema.
     */
    @Test
    void siblingIsAppliedOnEveryLevelOfRecursion() {
        assertEquals(List.of(), messages(validate("nodeRef", Map.of("id", "1", "next", Map.of("id", "2")))));

        // "next" requires an id next to its $ref
        ValidationError error = single(validate("nodeRef", Map.of("id", "1", "next", Map.of())));
        assertEquals("Required property id is missing.", error.getMessage());
        assertEquals("REQUEST/BODY#/nodeRef/next/id", error.getContext().getLocationForRequest());

        error = single(validate("nodeRef", Map.of("id", "1", "next", Map.of("id", "2", "next", Map.of()))));
        assertEquals("Required property id is missing.", error.getMessage());
        assertEquals("REQUEST/BODY#/nodeRef/next/next/id", error.getContext().getLocationForRequest());
    }

    /**
     * Not only the siblings: the referenced schema itself keeps applying below the first
     * repetition, so Node's own `id: string` is validated at every level.
     */
    @Test
    void refTargetIsAppliedOnEveryLevelOfRecursion() {
        ValidationError error = single(validate("nodeRef", Map.of("id", "1", "next", Map.of("id", 42))));
        assertEquals("42 is of type integer which does not match any of [string]", error.getMessage());
        assertEquals("REQUEST/BODY#/nodeRef/next/id", error.getContext().getLocationForRequest());
    }

    /**
     * readOnly is read by the surrounding ObjectValidator, not by the SchemaValidator that resolves
     * the $ref. The error has to stay a single one although two schemas are validated.
     */
    @Test
    void siblingReadOnlyIsHonoured() {
        ValidationError error = single(validate("readOnlyRef", ADDRESS));
        assertTrue(error.getMessage().startsWith("The property readOnlyRef is read only."),
                "Unexpected message: " + error.getMessage());
        assertEquals("REQUEST/BODY#/readOnlyRef", error.getContext().getLocationForRequest());
        assertEquals(BODY, error.getContext().getValidatedEntityType());
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
