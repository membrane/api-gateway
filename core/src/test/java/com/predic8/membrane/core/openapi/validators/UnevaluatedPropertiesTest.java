/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.openapi.model.Request;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;

class UnevaluatedPropertiesTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/unevaluated-properties.yaml";
    }

    private ValidationErrors post(String path, String body) throws ParseException {
        return validator.validate(Request.post().mediaType(APPLICATION_JSON).path(path).body(body));
    }

    private void assertRejected(String path, String body, String property) throws ParseException {
        final var errors = post(path, body);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.toString().contains(property), errors.toString());
    }

    @Test
    void falseRejectsUnknownProperty() throws ParseException {
        final var errors = post("/closed", """
                {"name": "a", "foo": 1}
                """);
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("/foo", errors.getFirst().getContext().getJSONpointer());
        assertEquals("The property foo is not allowed. The object does not allow unevaluated properties.",
                errors.getFirst().getMessage());
    }

    @Test
    void falseRejectsEachUnknownProperty() throws ParseException {
        final var errors = post("/closed", """
                {"name": "a", "foo": 1, "bar": 2}
                """);
        assertEquals(2, errors.size(), errors.toString());
        assertEquals(Set.of("/foo", "/bar"),
                errors.stream().map(e -> e.getContext().getJSONpointer()).collect(toSet()));
    }

    @Test
    void falseAcceptsDeclaredAndPatternProperties() throws ParseException {
        assertEquals(0, post("/closed", """
                {"name": "a", "x-trace": "1"}
                """).size());
    }

    @Test
    void allOfContributesEvaluatedProperties() throws ParseException {
        assertEquals(0, post("/allof", """
                {"id": "1", "name": "a"}
                """).size());
    }

    @Test
    void allOfRejectsPropertyNoSubschemaEvaluates() throws ParseException {
        assertRejected("/allof", """
                {"id": "1", "name": "a", "foo": 1}
                """, "foo");
    }

    @Test
    void schemaValidatesUnevaluatedProperties() throws ParseException {
        assertEquals(0, post("/typed", """
                {"name": "a", "foo": 1}
                """).size());
    }

    @Test
    void schemaRejectsInvalidUnevaluatedProperty() throws ParseException {
        assertRejected("/typed", """
                {"name": "a", "foo": "bar"}
                """, "foo");
    }

    @Test
    void anyOfCountsOnlyMatchingBranch() throws ParseException {
        assertEquals(0, post("/anyof", """
                {"kind": "cat", "lives": 9}
                """).size());
    }

    @Test
    void anyOfIgnoresPropertiesOfNonMatchingBranch() throws ParseException {
        assertRejected("/anyof", """
                {"kind": "cat", "tricks": 3}
                """, "tricks");
    }

    @Test
    void thenCountsWhenIfMatches() throws ParseException {
        assertEquals(0, post("/ifthen", """
                {"kind": "cat", "lives": 9}
                """).size());
    }

    @Test
    void thenIgnoredWhenIfDoesNotMatch() throws ParseException {
        assertRejected("/ifthen", """
                {"kind": "dog", "lives": 9}
                """, "lives");
    }

    /**
     * Nothing validates if/then/else, so then only evaluates the properties of a node that is valid against it.
     */
    @Test
    void thenIgnoredWhenNodeIsInvalidAgainstIt() throws ParseException {
        assertRejected("/ifthen", """
                {"kind": "cat", "lives": "wrong"}
                """, "lives");
    }

    @Test
    void dependentSchemaCountsWhenNodeIsValidAgainstIt() throws ParseException {
        assertEquals(0, post("/dependent", """
                {"kind": "cat", "lives": 9}
                """).size());
    }

    @Test
    void dependentSchemaIgnoredWhenNodeIsInvalidAgainstIt() throws ParseException {
        assertRejected("/dependent", """
                {"kind": "cat", "lives": "wrong"}
                """, "lives");
    }

    /**
     * An unresolvable $ref evaluates no properties, it must not break the collection of the others.
     */
    @Test
    void unresolvableRefEvaluatesNothing() throws ParseException {
        assertRejected("/dangling-ref", """
                {"name": "a", "foo": 1}
                """, "property foo is not allowed");
    }

    @Test
    void discriminatorByComponentNameCountsSelectedSchema() throws ParseException {
        assertEquals(0, post("/discriminator", """
                {"petType": "Cat", "lives": 9}
                """).size());
    }

    @Test
    void discriminatorByMappingCountsSelectedSchema() throws ParseException {
        assertEquals(0, post("/discriminator", """
                {"petType": "dog", "tricks": 3}
                """).size());
    }

    @Test
    void discriminatorIgnoresPropertiesOfOtherSchemas() throws ParseException {
        assertRejected("/discriminator", """
                {"petType": "Cat", "tricks": 3}
                """, "property tricks is not allowed");
    }

    @Test
    void discriminatorWithOneOfCountsSelectedSchema() throws ParseException {
        assertEquals(0, post("/discriminator-oneof", """
                {"petType": "Lion", "lives": 9}
                """).size());
    }

    /**
     * Validation dispatches to the selected schema only, so the other oneOf branches evaluate nothing.
     */
    @Test
    void discriminatorWithOneOfIgnoresPropertiesOfOtherBranches() throws ParseException {
        assertRejected("/discriminator-oneof", """
                {"petType": "Lion", "lives": 9, "tricks": 3}
                """, "property tricks is not allowed");
    }

    @Test
    void additionalPropertiesEvaluatesEverything() throws ParseException {
        assertEquals(0, post("/additional", """
                {"foo": "bar"}
                """).size());
    }

    /**
     * Each level of the tree matches its oneOf branches. Validating them again for unevaluatedProperties must not
     * double the work per level, see #3482.
     */
    @Test
    void deepRecursiveTreeValidatesInLinearTime() {
        assertTimeoutPreemptively(ofSeconds(10), () -> assertEquals(0, post("/tree", tree(40, "")).size()));
    }

    @Test
    void deepRecursiveTreeRejectsUnevaluatedPropertyInLinearTime() {
        assertTimeoutPreemptively(ofSeconds(10), () -> assertFalse(post("/tree", tree(40, ", \"foo\": 1")).isEmpty()));
    }

    /**
     * Like above, but the oneOf is in a component referenced through allOf, so the unevaluatedProperties collector
     * reaches it by following the $ref.
     */
    @Test
    void deepRecursiveTreeThroughAllOfRefValidatesInLinearTime() {
        assertTimeoutPreemptively(ofSeconds(10), () -> assertEquals(0, post("/tree-all-of", tree(40, "")).size()));
    }

    @Test
    void deepRecursiveTreeThroughAllOfRefRejectsUnevaluatedPropertyInLinearTime() {
        assertTimeoutPreemptively(ofSeconds(10), () -> assertFalse(post("/tree-all-of", tree(40, ", \"foo\": 1")).isEmpty()));
    }

    /**
     * @return a tree of nested branches, depth levels deep, whose leaf has the additional members leafExtra
     */
    private static String tree(int depth, String leafExtra) {
        return "{\"children\": [".repeat(depth) + "{\"value\": \"a\"%s}".formatted(leafExtra) + "]}".repeat(depth);
    }
}
