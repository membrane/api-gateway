/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.graphql;

import com.predic8.membrane.core.graphql.model.*;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.google.common.collect.ImmutableList.of;
import static com.predic8.membrane.core.graphql.GraphQLUtil.insertErrorMarker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExecutableDocumentsParsingTest {
    @Test
    public void typeAndFieldWithDescription() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(null, null, null, null, of(
                        new Field( "foo", null)))),
                """
                { foo }""");
    }

    @Test
    public void unclosedSelection() throws IOException, ParsingException {
        failingTest("""
                { foo ^""");
    }

    @Test
    public void invalidSelection() throws IOException, ParsingException {
        failingTest("""
                { 123^\040""");
    }

    @Test
    public void explicitQuery() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),
                        null, null, null, of(
                        new Field( "foo", null)))),
                """
                query { foo }""");
    }

    @Test
    public void invalidQuery1() throws IOException, ParsingException {
        failingTest("""
                query 123^ { foo }""");
    }

    @Test
    public void namedQuery() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),
                        "TheQuery", null, null, of(
                        new Field( "foo", null)))),
                """
                query TheQuery { foo }""");
    }

    @Test
    public void mutation() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("mutation"),
                        "foo", null, null, of(
                        new Field( "bar", null)))),
                """
                mutation foo { bar }""");
    }

    @Test
    public void subscription() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("subscription"),
                        "foo", null, null, of(
                        new Field( "bar", null)))),
                """
                subscription foo { bar }""");
    }

    @Test
    public void invalidOperationName() throws IOException, ParsingException {
        failingTest("""
                somequery^ { foo }""");
    }

    @Test
    public void namedQueryWithDirectives() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),
                        "TheQuery", null, of(
                        new Directive("dir1"),
                        new Directive("dir2")
                ), of(
                        new Field("foo", null)))),
                """
                        query TheQuery @dir1 @dir2 { foo }""");
    }

    @Test
    public void invalidDirective() throws IOException, ParsingException {
        failingTest("""
                query foo @123^ { bar }""");
    }


    @Test
    public void namedQueryWithVariables1() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),"TheQuery", of(
                        new VariableDefinition(new Variable("devicePicSize"), new NamedType("Int"), null, null)
                ), null, of(
                        new Field("foo", null)))),
                """
                        query TheQuery($devicePicSize: Int) { foo }""");
    }

    @Test
    public void namedQueryWithVariables2() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),"q", of(
                        new VariableDefinition(new Variable("a"), new NamedType("Int"), new IntValue(10), of(
                                new Directive("dir1"),
                                new Directive("dir2", of(new Argument("a", new StringValue("b"))))
                        )),
                        new VariableDefinition(new Variable("b"), new NamedType("String"), null, null)
                ), null, of(
                        new Field("foo", null)))),
                """
                        query q($a: Int = 10 @dir1 @dir2(a:"b"), $b: String) { foo }""");
    }

    @Test
    public void multipleFields() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(null, null, null, null, of(
                        new Field( "foo", null), new Field( "bar", null), new Field( "baz", null)))),
                """
                { foo bar baz}""");
    }

    @Test
    public void alias() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(null, null, null, null, of(
                        new Field("foo", "bar", null, null, null),
                        new Field( "baz1", "baz2", null, null, null)))),
                """
                { foo : bar baz1:baz2}""");
    }

    @Test
    public void badField() throws IOException {
        failingTest("""
                { foo : 123^ baz1:baz2}""");
    }

    @Test
    public void fieldWithArguments() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),
                        null, null, null, of(
                        new Field( null, "foo", of(new Argument("id", new IntValue(4))), null, null)))),
                """
                query { foo(id: 4) }""");
    }

    @Test
    public void fieldWithDirective() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),
                        null, null, null, of(
                        new Field( null, "foo", null, of(new Directive("dir1")), null)))),
                """
                query { foo @dir1 }""");
    }

    @Test
    public void fieldWithSelectionSet() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),
                        null, null, null, of(
                        new Field( "foo", of(new Field("bar", null)))))),
                """
                query { foo { bar } }""");
    }

    @Test
    public void fieldWithEverything() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(new OperationType("query"),
                        null, null, null, of(
                        new Field( "bar", "foo", of(new Argument("id", new IntValue(4))),
                                of(new Directive("dir1")), of(new Field("baz", null)))))),
                """
                query { bar: foo(id: 4) @dir1 { baz } }""");
    }

    @Test
    public void multipleQueries() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(
                        new OperationDefinition(new OperationType("query"), "Q1", null, null, of(
                                new Field( "foo", null), new Field( "bar", null))),
                        new OperationDefinition(new OperationType("query"), "Q2", null, null, of(
                                new Field( "foo2", null), new Field( "bar2", null)))
                ),
                """
                query Q1 { foo bar } query Q2 { foo2 bar2 }""");
    }

    @Test
    public void multipleQueriesMustBeNamed() throws IOException, ParsingException {
        failingTest("""
                query { foo } query { bar }^""");
    }

    @Test
    public void multipleShorthandQueries() throws IOException, ParsingException {
        failingTest("""
                { foo } {^ bar }""");
    }

    @Test
    public void fragment() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new FragmentDefinition("foo", new NamedType("Type1"), null, of(
                        new Field(null, "bar", of(new Argument("a", new IntValue(1))), null, null),
                        new Field("baz", null)))),
                """
                fragment foo on Type1 {
                  bar(a: 1)
                  baz
                }""");
    }

    @Test
    public void fragmentWithDirective() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new FragmentDefinition("foo", new NamedType("Type1"),
                        of(new Directive("dir1")), of(new Field("bar", null), new Field("baz", null)))),
                """
                fragment foo on Type1 @dir1 {
                  bar
                  baz
                }""");
    }

    @Test
    public void fragmentWithBadName() throws IOException, ParsingException {
        failingTest("""
                fragment on^ on Type1 { foo }""");
    }

    @Test
    public void fragmentWithBadName2() throws IOException, ParsingException {
        failingTest("""
                fragment 123^ on Type1 { foo }""");
    }

    @Test
    public void fragmentWithoutSelection() throws IOException, ParsingException {
        failingTest("""
                fragment Frag on Type1 ^""");
    }

    @Test
    public void fragmentWithoutSelection2() throws IOException, ParsingException {
        failingTest("""
                fragment Frag on Type1
                fragment^ Frag2 on Type2 { }""");
    }

    @Test
    public void typeWithFragmentSpread() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(null, null, null, null, of(
                        new Field( "foo", of(
                                new FragmentSpread("fooFields", null),
                                new Field("bar", null)))))),
                """
                { foo { ...fooFields bar } }""");
    }

    @Test
    public void inlineFragmentWithTypeCondition() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(null, null, null, null, of(
                        new Field( "foo", of(
                                new InlineFragment(new NamedType("T"), null, of(new Field("baz", null))),
                                new Field("bar", null)))))),
                """
                { foo { ... on T { baz } bar } }""");
    }

    @Test
    public void inlineFragment() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(null, null, null, null, of(
                        new Field( "foo", of(
                                new InlineFragment(null, null, of(new Field("baz", null))),
                                new Field("bar", null)))))),
                """
                { foo { ... { baz } bar } }""");
    }

    @Test
    public void inlineFragmentWithDirective() throws IOException, ParsingException {
        attemptParse(new ExecutableDocument(new OperationDefinition(null, null, null, null, of(
                        new Field( "foo", of(
                                new InlineFragment(null, of(new Directive("dir1")), of(new Field("baz", null))),
                                new Field("bar", null)))))),
                """
                { foo { ... @dir1 { baz } bar } }""");
    }

    @Test
    public void badInlineFragment() throws IOException {
        failingTest("""
                { ... on Type1 123^ }""");
    }

    private void attemptParse(ExecutableDocument expected, String schemaDoc) throws IOException, ParsingException {
        ExecutableDocument ed = new GraphQLParser().parseRequest(new ByteArrayInputStream(schemaDoc.getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected, ed);
    }

    /**
     * @param schemaDocWithErrorMarker a GraphQL schema document containing a '^' character at the position where parsing should fail.
     */
    private void failingTest(String schemaDocWithErrorMarker) throws IOException {
        String schemaDoc = schemaDocWithErrorMarker.replaceAll("\\^", "");
        ParsingException pe = assertThrows(ParsingException.class, () -> new GraphQLParser().parseRequest(new ByteArrayInputStream(schemaDoc.getBytes(StandardCharsets.UTF_8))));
        assertEquals(schemaDocWithErrorMarker, insertErrorMarker(schemaDoc, (int) pe.getPosition()));
    }

}
