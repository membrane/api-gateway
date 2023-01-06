package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParserUtil;
import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.*;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class Field extends Selection {
    String alias;
    String name;
    List<Argument> arguments;
    List<Directive> directives;
    List<Selection> selections;

    public Field() {
    }

    public Field(String name, List<Selection> selections) {
        this.name = name;
        this.selections = selections;
    }

    public Field(String alias, String name, List<Argument> arguments, List<Directive> directives, List<Selection> selections) {
        this.alias = alias;
        this.name = name;
        this.arguments = arguments;
        this.directives = directives;
        this.selections = selections;
    }

    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        if (tokenizer.type() != NAME)
            throw new ParsingException("Expected name.", tokenizer.position());
        String s = tokenizer.string();

        if (!tokenizer.advance()) {
            name = s;
            return;
        }

        if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == ':') {
            alias = s;
            tokenizer.mustAdvance();
            if (tokenizer.type() != NAME)
                throw new ParsingException("Expected name.", tokenizer.position());
            name = tokenizer.string();
            if (!tokenizer.advance())
                return;
        } else {
            name = s;
        }

        arguments = parseOptionalArguments(tokenizer);
        if (arguments != null)
            if (!tokenizer.advance())
                return;

        directives = parseDirectivesOpt(tokenizer);
        if (directives != null)
            if (!tokenizer.advance())
                return;

        selections = parseSelectionSetOpt(tokenizer);

        if (selections == null)
            tokenizer.revert();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Field field = (Field) o;
        return Objects.equals(alias, field.alias) && Objects.equals(name, field.name) && Objects.equals(arguments, field.arguments) && Objects.equals(directives, field.directives) && Objects.equals(selections, field.selections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alias, name, arguments, directives, selections);
    }

    @Override
    public String toString() {
        return "Field{" +
                "alias='" + alias + '\'' +
                ", name='" + name + '\'' +
                ", arguments=" + arguments +
                ", directives=" + directives +
                ", selections=" + selections +
                '}';
    }

    public String getName() {
        return name;
    }

    public List<Selection> getSelections() {
        return selections;
    }
}
