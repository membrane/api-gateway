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

public class DirectiveDefinition implements TypeSystemDefinition {
    private String description;
    private String name;
    private List<InputValueDefinition> inputValueDefinitions;
    private boolean repeatable;
    private List<DirectiveLocation> directiveLocations;

    public DirectiveDefinition() {
    }

    public DirectiveDefinition(String description, String name, List<InputValueDefinition> inputValueDefinitions, boolean repeatable, List<DirectiveLocation> directiveLocations) {
        this.description = description;
        this.name = name;
        this.inputValueDefinitions = inputValueDefinitions;
        this.repeatable = repeatable;
        this.directiveLocations = directiveLocations;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != '@')
            throw new ParsingException("Expected '@'.", tokenizer.position());

        name = parseName(tokenizer);

        tokenizer.mustAdvance();
        inputValueDefinitions = parseOptionalArgumentsDefinition(tokenizer);
        if (inputValueDefinitions != null) {
            tokenizer.mustAdvance();
        }

        if (tokenizer.type() == NAME && tokenizer.string().equals("repeatable")) {
            repeatable = true;
            tokenizer.mustAdvance();
        }

        if (tokenizer.type() != NAME || !tokenizer.string().equals("on"))
            throw new ParsingException("Expected 'on'.", tokenizer.position());

        directiveLocations = parseDirectiveLocations(tokenizer);

        tokenizer.revert();
    }

    public static List<DirectiveLocation> parseDirectiveLocations(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '|')
            tokenizer.mustAdvance();
        List<DirectiveLocation> res = new ArrayList<>();
        while(true) {
            DirectiveLocation type = DirectiveLocation.parseDirectiveLocation(tokenizer);
            res.add(type);

            if (!tokenizer.advance())
                break;
            if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != '|')
                break;
            tokenizer.mustAdvance();
        }
        return res;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectiveDefinition that = (DirectiveDefinition) o;
        return repeatable == that.repeatable && Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(inputValueDefinitions, that.inputValueDefinitions) && Objects.equals(directiveLocations, that.directiveLocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, inputValueDefinitions, repeatable, directiveLocations);
    }

    @Override
    public String toString() {
        return "DirectiveDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", inputValueDefinitions=" + inputValueDefinitions +
                ", repeatable=" + repeatable +
                ", directiveLocations=" + directiveLocations +
                '}';
    }
}
