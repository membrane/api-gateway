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

package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class ExecutableDocumentTest {

    @Test
    void getFragmentDefinitions() throws Exception {
        final var ed = parse("query { ...a } fragment a on Query { x } fragment b on Query { y }");
        assertEquals(List.of("a", "b"), ed.getFragmentDefinitions().stream().map(FragmentDefinition::getName).toList());
    }

    @Test
    void getFragmentDefinition() throws Exception {
        final var ed = parse("query { ...a } fragment a on Query { x } fragment b on Query { y }");
        assertEquals("b", ed.getFragmentDefinition("b").orElseThrow().getName());
        assertTrue(ed.getFragmentDefinition("c").isEmpty());
    }

    @Test
    void collectFieldsPlainFields() throws Exception {
        assertEquals(List.of("a", "b"), collectRootFieldNames("{ a b }"));
    }

    @Test
    void collectFieldsDoesNotEnterSubSelections() throws Exception {
        assertEquals(List.of("a", "c"), collectRootFieldNames("{ a { b } c }"));
    }

    @Test
    void collectFieldsExpandsInlineFragment() throws Exception {
        assertEquals(List.of("a", "b", "c"), collectRootFieldNames("{ a ... on Query { b c } }"));
    }

    @Test
    void collectFieldsExpandsNamedFragment() throws Exception {
        assertEquals(List.of("a", "b", "c"), collectRootFieldNames("query { a ...F } fragment F on Query { b c }"));
    }

    @Test
    void collectFieldsExpandsNestedFragments() throws Exception {
        assertEquals(List.of("a", "b", "c"), collectRootFieldNames("""
                query { ...F }
                fragment F on Query { a ... on Query { b ...G } }
                fragment G on Query { c }"""));
    }

    @Test
    void collectFieldsExpandsNamedFragmentOnlyOnce() throws Exception {
        assertEquals(List.of("a"), collectRootFieldNames("query { ...F ... on Query { ...F } } fragment F on Query { a }"));
    }

    @Test
    void collectFieldsTerminatesOnCyclicFragments() throws Exception {
        assertEquals(List.of("a", "b"), collectRootFieldNames("""
                query { ...A }
                fragment A on Query { a ...B }
                fragment B on Query { b ...A }"""));
    }

    @Test
    void collectFieldsIsLinearForRepeatedSpreads() throws Exception {
        // Each fragment spreads the next one twice: a naive expansion would visit 2^40 spreads.
        final var levels = 40;
        final var doc = new StringBuilder("query { ...F0 }");
        for (var i = 0; i < levels; i++)
            doc.append(" fragment F%d on Query { ...F%d ...F%d }".formatted(i, i + 1, i + 1));
        doc.append(" fragment F%d on Query { x }".formatted(levels));

        assertEquals(List.of("x"), collectRootFieldNames(doc.toString()));
    }

    @Test
    void collectFieldsFailsOnUndefinedFragment() throws Exception {
        final var e = assertThrows(GraphQLOverHttpValidationException.class, () -> collectRootFieldNames("query { ...missing }"));
        assertEquals("Did not find fragment 'missing'.", e.getMessage());
    }

    private static List<String> collectRootFieldNames(String document) throws Exception {
        final var ed = parse(document);
        return ed.collectFields(ed.getOperationDefinitions().getFirst().getSelections()).stream()
                .map(Field::getName)
                .toList();
    }

    private static ExecutableDocument parse(String document) throws IOException, ParsingException {
        final var ed = new ExecutableDocument();
        ed.parse(new Tokenizer(new ByteArrayInputStream(document.getBytes(UTF_8))));
        return ed;
    }
}
