package com.predic8.membrane.core.graphql;

import com.predic8.membrane.core.graphql.model.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.google.common.collect.ImmutableList.of;
import static com.predic8.membrane.core.graphql.GraphQLUtil.insertErrorMarker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TypeSystemDocumentsParsingTest {

    @Test
    public void typeAndFieldWithDescription() throws IOException, ParsingException {
        attemptParse(
                new TypeSystemDocument(new ObjectTypeDefinition("desc", "Type1", null, null, of(
                        new FieldDefinition("description", "field1", null, new NamedType("String"), null),
                        new FieldDefinition(null, "field2", null, new NamedType("String"), null)
                ) )),
                """
                \"""desc\"""
                type Type1 {
                  "description"
                  field1: String
                  field2: String
                }""");
    }

    @Test
    public void typeImplements() throws IOException, ParsingException {
        attemptParse(
                new TypeSystemDocument(new ObjectTypeDefinition("desc", "Type1", of(new NamedType("Type2")), null, of(
                        new FieldDefinition(null, "field1", null, new NamedType("String"), null)
                ) )),
                """
                \"""desc\"""
                type Type1 implements Type2 {
                  field1: String
                }""");
    }

    @Test
    public void typeImplementsMultiple() throws IOException, ParsingException {
        attemptParse(
                new TypeSystemDocument(new ObjectTypeDefinition("desc", "Type1", of(new NamedType("Type2"), new NamedType("Type3")), null, of(
                        new FieldDefinition(null, "field1", null, new NamedType("String"), null)
                ) )),
                """
                \"""desc\"""
                type Type1 implements & Type2 & Type3 {
                  field1: String
                }""");
    }

    @Test
    public void directiveWithAllKindsOfValues() throws IOException, ParsingException {
        attemptParse(
                new TypeSystemDocument(
                        new ObjectTypeDefinition(null, "Type1", null, of(
                                new Directive("dir1", null),
                                new Directive("dir2", of(
                                        new Argument("abc", new StringValue("def")),
                                        new Argument("geh", new IntValue(123)),
                                        new Argument("ijk", new EnumValue("DEMO")),
                                        new Argument("lmn", new BooleanValue(true)),
                                        new Argument("opq", new ListValue()),
                                        new Argument("rst", new ListValue(of(new IntValue(1), new IntValue(2)))),
                                        new Argument("uvw", new ObjectValue(of())),
                                        new Argument("xyz", new ObjectValue(of(
                                                new ObjectField("a", new StringValue("b")),
                                                new ObjectField("c", new StringValue("d"))))),
                                        new Argument("ABC", new NullValue())
                                ))), of()),
                        new ObjectTypeDefinition(null, "Type2", null, null, of()),
                        new ObjectTypeDefinition(null, "Type3", null, null, of())
                ),
                """
                type Type1 @dir1 @dir2(abc: "def", geh: 123, ijk: DEMO, lmn: true, opq: [], rst: [1,2], uvw: {}, xyz: {a:"b", c:"d"}, ABC: null)
                type Type2
                type Type3""");
    }

    @Test
    public void fieldWithInputValueDefinitionsAndDirective() throws IOException, ParsingException {
        attemptParse(
                new TypeSystemDocument(new ObjectTypeDefinition(null, "Type1", null, null, of(
                        new FieldDefinition("description", "field1", of(
                                new InputValueDefinition("desc", "abc", new NamedType("String"), null, null),
                                new InputValueDefinition(null, "geh", new NamedType("Int"), new IntValue(10), of(
                                        new Directive("directive3", of(new Argument("xyz", new IntValue(123))))
                                ))
                        ), new NamedType("String"), of(
                                new Directive("directive4", of(new Argument("ijk", new FloatValue(1.1)))))),
                        new FieldDefinition(null, "field2", null, new NamedType("String"), null)
                ) )),
                """
                type Type1 {
                  "description"
                  field1("desc" abc: String, geh: Int = 10 @directive3(xyz: 123)): String @directive4(ijk: 1.1)
                  field2: String
                }""");
    }

    @Test
    public void fieldWithListType() throws IOException, ParsingException {
        attemptParse(
                new TypeSystemDocument(new ObjectTypeDefinition(null, "Type1", null, null, of(
                        new FieldDefinition(null, "field1", null, new ListType(new NamedType("String")), null),
                        new FieldDefinition(null, "field2", null, new NamedType("String"), null)
                ) )),
                """
                type Type1 {
                  field1: [String]
                  field2: String
                }""");
    }

    @Test
    public void directiveAtEOF() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new ObjectTypeDefinition(null, "Type1", null, of(
                                new Directive("directive", null)), of())),
                "type Type1 @directive"
        );
    }

    @Test
    public void nullableListItem() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new ObjectTypeDefinition(null, "Type1", null, null, of(
                                new FieldDefinition(null, "field1", null,
                                        new ListType(new NamedType("String", true)), null)
                        ))),
                "type Type1 { field1: [ String! ] }"
        );
    }

    @Test
    public void nullableListItemAndList() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new ObjectTypeDefinition(null, "Type1", null, null, of(
                                new FieldDefinition(null, "field1", null,
                                        new ListType(new NamedType("String", true), true), null)
                        ))),
                "type Type1 { field1: [ String! ]! }"
        );
    }

    @Test
    public void input() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new InputObjectTypeDefinition(null, "Type1", null, of(
                                new InputValueDefinition(null, "a",
                                        new NamedType("Float"), null, null)
                        ))),
                "input Type1 { a: Float }"
        );
    }

    @Test
    public void inputWithoutFields() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new InputObjectTypeDefinition(null, "Type1", null, of()),
                        new InputObjectTypeDefinition(null, "Type2", null, of())
                ),
                "input Type1 input Type2"
        );
    }

    @Test
    public void inputWithDescriptionAndDirective() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new InputObjectTypeDefinition("description", "Type1",
                                of(new Directive("dir", null)),
                                of(new InputValueDefinition(null, "a",
                                        new NamedType("Float"), null, null)))

                ),
                "\"description\" input Type1 @dir { a: Float }"
        );
    }
    @Test
    public void scalarWithDirective() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new ScalarTypeDefinition("description", "Type1",
                                of(new Directive("dir", null)))
                ),
                "\"description\" scalar Type1 @dir"
        );
    }

    @Test
    public void scalarWithDirectiveAndType() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new ScalarTypeDefinition("description", "Type1",
                                of(new Directive("dir", null))),
                        new ObjectTypeDefinition(null, "Type2", null, null, of())
                ),
                "\"description\" scalar Type1 @dir type Type2"
        );
    }

    @Test
    public void scalarAndType() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new ScalarTypeDefinition(null, "Type1", null),
                        new ObjectTypeDefinition(null, "Type2", null, null, of())
                ),
                "scalar Type1 type Type2"
        );
    }

    @Test
    public void enum1() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new EnumTypeDefinition("description", "Type1", of(new Directive("dir", null)), of())
                        ),
                "\"description\" enum Type1 @dir"
        );
    }

    @Test
    public void enum2() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new EnumTypeDefinition(null, "Type1", null, of())
                ),
                "enum Type1"
        );
    }

    @Test
    public void enum3() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new EnumTypeDefinition(null, "Type1", null, of(
                                new EnumValueDefinition(null, new EnumValue("A"), null),
                                new EnumValueDefinition(null, new EnumValue("B"), null)
                        ))
                ),
                "enum Type1 { A B }"
        );
    }

    @Test
    public void enum4() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new EnumTypeDefinition(null, "Type1", of(
                                new Directive("dir", null)
                        ), of(
                                new EnumValueDefinition("description", new EnumValue("A"), of(
                                        new Directive("dir2", null)
                                )),
                                new EnumValueDefinition("description2", new EnumValue("B"), of(
                                        new Directive("dir3", null)
                                ))
                        ))
                ),
                "enum Type1 @dir { \"description\" A @dir2 \"description2\" B @dir3 }"
        );
    }

    @Test
    public void enum5() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new EnumTypeDefinition(null, "Type1", null, of()),
                        new ObjectTypeDefinition(null, "Type2", null, null, of())),
                "enum Type1 type Type2"
        );
    }

    @Test
    public void enum6() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new EnumTypeDefinition(null, "Type1", null, of(
                                new EnumValueDefinition(null, new EnumValue("A"), null)
                        )),
                        new ObjectTypeDefinition(null, "Type2", null, null, of())),
                "enum Type1 { A } type Type2"
        );
    }

    @Test
    public void schema() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new SchemaDefinition(null, null, of(
                                new RootOperationTypeDefinition(new OperationType("query"), new NamedType("A")),
                                new RootOperationTypeDefinition(new OperationType("query"), new NamedType("B"))
                        )),
                        new ObjectTypeDefinition(null, "Type2", null, null, of())),
                "schema { query: A query: B } type Type2"
        );
    }

    @Test
    public void invalidSchema1() throws IOException, ParsingException {
        failingTest(
                "schema { query A^ query: B } type Type2"
        );
    }

    @Test
    public void invalidSchema2() throws IOException, ParsingException {
        failingTest(
                "schema { query 123^ query: B } type Type2"
        );
    }

    @Test
    public void intrface() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new InterfaceTypeDefinition(null, "Interface1", null, null, of(
                                new FieldDefinition(null, "field", null, new NamedType("String"), null)
                        ))),
                "interface Interface1 { field: String }"
        );
    }

    @Test
    public void intrfaceWithImplements() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new InterfaceTypeDefinition(null, "Interface1", of(new NamedType("Interface2")), null, of(
                                new FieldDefinition(null, "field", null, new NamedType("String"), null)
                        ))),
                "interface Interface1 implements Interface2 { field: String }"
        );
    }

    @Test
    public void intrfaceWithDirective() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new InterfaceTypeDefinition(null, "Interface1", null,
                                of(new Directive("dir1")), of())),
                "interface Interface1 @dir1"
        );
    }

    @Test
    public void intrfaceWithDirective2() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new InterfaceTypeDefinition(null, "Interface1", null,
                                of(new Directive("dir1")), of()),
                        new InterfaceTypeDefinition(null, "I2", null, null, of())),
                "interface Interface1 @dir1 interface I2"
        );
    }

    @Test
    public void union1() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new UnionTypeDefinition(null, "A", null, of(new NamedType("B"), new NamedType("C")))),
                "union A = B | C"
        );
    }

    @Test
    public void union2() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new UnionTypeDefinition(null, "A", null, of(new NamedType("B"), new NamedType("C"))),
                        new ObjectTypeDefinition(null, "D", null, null, of())),
                "union A = | B | C type D"
        );
    }

    @Test
    public void union3() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new UnionTypeDefinition("desc", "A", null, of()),
                        new ObjectTypeDefinition(null, "D", null, null, of())),
                "\"desc\" union A type D"
        );
    }

    @Test
    public void union4() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new UnionTypeDefinition("desc", "A", of(new Directive("dir", null)), of()),
                        new ObjectTypeDefinition(null, "D", null, null, of())),
                "\"desc\" union A @dir type D"
        );
    }

    @Test
    public void directive1() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new DirectiveDefinition(null, "a", of(
                                new InputValueDefinition(null, "b", new NamedType("Int"), null, null)
                        ), true, of(
                                new ExecutableDirectiveLocation("QUERY")
                        )),
                        new ObjectTypeDefinition(null, "D", null, null, of())),
                "directive @a(b: Int) repeatable on QUERY type D"
        );
    }

    @Test
    public void directive2() throws IOException, ParsingException {
        attemptParse(new TypeSystemDocument(
                        new DirectiveDefinition("desc", "a", null, false, of(
                                new ExecutableDirectiveLocation("QUERY"),
                                new TypeSystemDirectiveLocation("SCHEMA")
                        )),
                        new ObjectTypeDefinition(null, "D", null, null, of())),
                "\"desc\" directive @a on | QUERY | SCHEMA type D"
        );
    }

    @Test
    public void illegalChar() throws IOException {
        failingTest("type Type1 { field1: String \u0007^ }");
    }

    @Test
    public void illegalTypeNotClosed() throws IOException {
        failingTest("type Type1 { field1: String^");
    }

    @Test
    public void illegalTypeFieldInvalid() throws IOException {
        failingTest("type Type1 { 123^ }");
    }

    @Test
    public void illegalTypeName() throws IOException {
        failingTest("type 123^");
    }

    @Test
    public void illegalListTermination() throws IOException {
        failingTest("type Type1 { field1: [ String )^ }");
    }

    @Test
    public void illegalFieldWithoutColon() throws IOException {
        failingTest("type Type1 { field1 String^ }");
    }

    @Test
    public void illegalArgumentWithoutColon() throws IOException {
        failingTest("type Type1 { field1(abc String^) : String }");
    }

    @Test
    public void illegalEnumValue() throws IOException {
        failingTest("enum Type1 { 123^ }");
    }

    @Test
    public void illegalEnumNotClosed() throws IOException {
        failingTest("enum Type1 { 123^");
    }

    @Test
    public void illegalStructure() throws IOException {
        failingTest("123^ { }");
    }

    @Test
    public void illegalMultilineString() throws IOException {
        failingTest("\"multiline\n^description\" type Type1");
    }

    @Test
    public void unicodeBOM() throws ParsingException, IOException {
        attemptParse(new TypeSystemDocument(
                        new ObjectTypeDefinition(null, "A", null, null, of())),
                "\uFEFFtype A"
        );
    }

    /**
     * @param schemaDocWithErrorMarker a GraphQL schema document containing a '^' character at the position where parsing should fail.
     */
    private void failingTest(String schemaDocWithErrorMarker) throws IOException {
        String schemaDoc = schemaDocWithErrorMarker.replaceAll("\\^", "");
        ParsingException pe = assertThrows(ParsingException.class, () -> {
            new GraphQLParser().parseSchema(new ByteArrayInputStream(schemaDoc.getBytes(StandardCharsets.UTF_8)));
        });
        assertEquals(schemaDocWithErrorMarker, insertErrorMarker(schemaDoc, (int) pe.getPosition()));
    }

    private void attemptParse(TypeSystemDocument expected, String schemaDoc) throws IOException, ParsingException {
        TypeSystemDocument tsd = new GraphQLParser().parseSchema(new ByteArrayInputStream(schemaDoc.getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected, tsd);
    }
}
