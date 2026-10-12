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

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertRejected("/closed", """
                {"name": "a", "foo": 1}
                """, "foo");
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
     * An unresolvable $ref evaluates no properties, it must not break the collection of the others.
     */
    @Test
    void unresolvableRefEvaluatesNothing() throws ParseException {
        assertRejected("/dangling-ref", """
                {"name": "a", "foo": 1}
                """, "unevaluated property: foo");
    }

    @Test
    void additionalPropertiesEvaluatesEverything() throws ParseException {
        assertEquals(0, post("/additional", """
                {"foo": "bar"}
                """).size());
    }
}
