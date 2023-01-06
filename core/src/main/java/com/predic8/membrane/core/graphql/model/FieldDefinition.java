package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.*;
import static com.predic8.membrane.core.graphql.ParserUtil.*;

public class FieldDefinition {
    String description;
    private String name;
    private List<InputValueDefinition> inputValueDefinitions;
    private Type type;
    private List<Directive> directives;

    public FieldDefinition() {
    }

    public FieldDefinition(String description, String name, List<InputValueDefinition> inputValueDefinitions, Type type, List<Directive> directives) {
        this.description = description;
        this.name = name;
        this.inputValueDefinitions = inputValueDefinitions;
        this.type = type;
        this.directives = directives;
    }

    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        if (tokenizer.type() == STRING_VALUE) {
            description = tokenizer.string();
            tokenizer.mustAdvance();
        }
        if (tokenizer.type() != NAME)
            throw new ParsingException("Expected field name.", tokenizer.position());
        name = tokenizer.string();

        tokenizer.mustAdvance();
        inputValueDefinitions = parseOptionalArgumentsDefinition(tokenizer);
        if (inputValueDefinitions != null) {
            tokenizer.mustAdvance();
        }

        if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != ':')
            throw new ParsingException("Expected ':'.", tokenizer.position());

        tokenizer.mustAdvance();
        type = parseType(tokenizer);

        if (!tokenizer.advance())
            return;

        directives = parseDirectivesConstOpt(tokenizer);
        if (directives == null)
            tokenizer.revert();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldDefinition that = (FieldDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(inputValueDefinitions, that.inputValueDefinitions) && Objects.equals(type, that.type) && Objects.equals(directives, that.directives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, inputValueDefinitions, type, directives);
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", inputValueDefinitions=" + inputValueDefinitions +
                ", type=" + type +
                ", directives=" + directives +
                '}';
    }
}
