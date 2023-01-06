package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.*;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class InterfaceTypeDefinition implements TypeSystemDefinition {
    private String description;
    private String name;
    private List<NamedType> implementsInterfaces;
    private List<Directive> directives;
    private List<FieldDefinition> fieldDefinitions = new ArrayList<>();

    public InterfaceTypeDefinition() {
    }

    public InterfaceTypeDefinition(String description, String name, List<NamedType> implementsInterfaces, List<Directive> directives, List<FieldDefinition> fieldDefinitions) {
        this.description = description;
        this.name = name;
        this.implementsInterfaces = implementsInterfaces;
        this.directives = directives;
        this.fieldDefinitions = fieldDefinitions;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {

        name = parseName(tokenizer);

        if (!tokenizer.advance())
            return;

        if (tokenizer.type() == NAME && tokenizer.string().equals("implements")) {
            implementsInterfaces = parseImplements(tokenizer);
        }

        directives = parseDirectivesConstOpt(tokenizer);
        if (directives != null) {
            if (!tokenizer.advance())
                return;
        }

        if (tokenizer.type() == PUNCTUATOR && tokenizer.integer() == '{') {
            fieldDefinitions = parseFieldsDefinition(tokenizer);
            return;
        }

        tokenizer.revert();
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InterfaceTypeDefinition that = (InterfaceTypeDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(implementsInterfaces, that.implementsInterfaces) && Objects.equals(directives, that.directives) && Objects.equals(fieldDefinitions, that.fieldDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, implementsInterfaces, directives, fieldDefinitions);
    }

    @Override
    public String toString() {
        return "InterfaceTypeDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", implementsInterfaces=" + implementsInterfaces +
                ", directives=" + directives +
                ", fieldDefinitions=" + fieldDefinitions +
                '}';
    }
}
