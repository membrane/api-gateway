package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.parseName;
import static com.predic8.membrane.core.graphql.ParserUtil.parseOptionalArguments;

public class Directive {
    private String name;
    private List<Argument> arguments;

    public Directive() {
    }

    public Directive(String name) {
        this.name = name;
    }

    public Directive(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public void parse(Tokenizer t) throws IOException, ParsingException {
        name = parseName(t);
        if (t.advance()) {
            arguments = parseOptionalArguments(t);
            if (arguments == null)
                t.revert();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Directive directive = (Directive) o;
        return Objects.equals(name, directive.name) && Objects.equals(arguments, directive.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arguments);
    }

    @Override
    public String toString() {
        return "Directive{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}