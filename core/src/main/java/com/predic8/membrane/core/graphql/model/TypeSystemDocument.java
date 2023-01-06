package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.STRING_VALUE;

public class TypeSystemDocument {

    List<TypeSystemDefinition> typeSystemDefinitions = new ArrayList<>();

    public TypeSystemDocument() {
    }

    public TypeSystemDocument(TypeSystemDefinition... typeSystemDefinitions) {
        this.typeSystemDefinitions.addAll(Arrays.asList(typeSystemDefinitions));
    }

    public void parse(Tokenizer t) throws IOException, ParsingException {
        while(true) {
            if (!t.advance())
                return;

            String description = null;

            if (t.type() == STRING_VALUE) {
                description = t.string();
                t.mustAdvance();
            }

            if (t.type() != NAME)
                throw new ParsingException("Expected a name.", t.position());

            String type = t.string();
            TypeSystemDefinition typeSystemDefinition = switch (type) {
                case "schema" -> new SchemaDefinition();
                case "type" -> new ObjectTypeDefinition();
                case "input" -> new InputObjectTypeDefinition();
                case "scalar" -> new ScalarTypeDefinition();
                case "enum" -> new EnumTypeDefinition();
                case "interface" -> new InterfaceTypeDefinition();
                case "union" -> new UnionTypeDefinition();
                case "directive" -> new DirectiveDefinition();
                default -> throw new ParsingException("Expected 'schema', 'scalar', 'type', 'interface', 'union', 'enum', 'input or 'directive'. Got '" + type + "'.", t.position());
            };

            typeSystemDefinition.parse(t);
            typeSystemDefinition.setDescription(description);
            typeSystemDefinitions.add(typeSystemDefinition);
        }
    }

    public List<TypeSystemDefinition> getDefinitions() {
        return typeSystemDefinitions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeSystemDocument that = (TypeSystemDocument) o;
        return typeSystemDefinitions.equals(that.typeSystemDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeSystemDefinitions);
    }

    @Override
    public String toString() {
        return "TypeSystemDocument{" +
                "typeSystemDefinitions=" + typeSystemDefinitions +
                '}';
    }
}
