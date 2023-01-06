package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.parseDirectivesConstOpt;
import static com.predic8.membrane.core.graphql.ParserUtil.parseName;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class InputObjectTypeDefinition implements TypeSystemDefinition {
    private String description;
    private String name;
    private List<Directive> directives;
    private List<InputValueDefinition> inputValueDefinitions = new ArrayList<>();

    public InputObjectTypeDefinition() {
    }

    public InputObjectTypeDefinition(String description, String name, List<Directive> directives, List<InputValueDefinition> inputValueDefinitions) {
        this.description = description;
        this.name = name;
        this.directives = directives;
        this.inputValueDefinitions = inputValueDefinitions;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {

        name = parseName(tokenizer);

        if (!tokenizer.advance())
            return;

        directives = parseDirectivesConstOpt(tokenizer);
        if (directives != null) {
            if (!tokenizer.advance())
                return;
        }

        if (tokenizer.type() == PUNCTUATOR && tokenizer.integer() == '{') {
            parseInputFieldsDefinition(tokenizer);
            return;
        }

        tokenizer.revert();
    }

    private void parseInputFieldsDefinition(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        while(true) {
            if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '}')
                return;

            InputValueDefinition ivd = new InputValueDefinition();
            ivd.parse(tokenizer);
            inputValueDefinitions.add(ivd);

            if (!tokenizer.advance())
                break;
        }
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputObjectTypeDefinition that = (InputObjectTypeDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(directives, that.directives) && Objects.equals(inputValueDefinitions, that.inputValueDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, directives, inputValueDefinitions);
    }

    @Override
    public String toString() {
        return "InputObjectTypeDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", directives=" + directives +
                ", inputValueDefinitions=" + inputValueDefinitions +
                '}';
    }
}
