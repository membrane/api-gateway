package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.*;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class InlineFragment extends Selection {
    Type condition;
    List<Directive> directives;
    List<Selection> selections;

    public InlineFragment() {
    }

    public InlineFragment(Type condition, List<Directive> directives, List<Selection> selections) {
        this.condition = condition;
        this.directives = directives;
        this.selections = selections;
    }

    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        if (tokenizer.type() == NAME && tokenizer.string().equals("on")) {
            tokenizer.mustAdvance();
            condition = parseType(tokenizer);
            tokenizer.mustAdvance();
        }

        directives = parseDirectivesOpt(tokenizer);
        if (directives != null)
            tokenizer.mustAdvance();

        if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != '{')
            throw new ParsingException("Expected '{'.", tokenizer.position());

        selections = parseSelectionSetOpt(tokenizer); // selections will always be found, since we are on a '{'
        // no need to revert, since selections cannot be null
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InlineFragment that = (InlineFragment) o;
        return Objects.equals(condition, that.condition) && Objects.equals(directives, that.directives) && Objects.equals(selections, that.selections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, directives, selections);
    }

    @Override
    public String toString() {
        return "InlineFragment{" +
                "condition=" + condition +
                ", directives=" + directives +
                ", selections=" + selections +
                '}';
    }

    public List<Selection> getSelections() {
        return selections;
    }
}
