package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.parseDirectivesConstOpt;
import static com.predic8.membrane.core.graphql.ParserUtil.parseName;

public class ScalarTypeDefinition implements TypeSystemDefinition {
    private String description;
    private String name;
    private List<Directive> directives;

    public ScalarTypeDefinition() {
    }

    public ScalarTypeDefinition(String description, String name, List<Directive> directives) {
        this.description = description;
        this.name = name;
        this.directives = directives;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {

        name = parseName(tokenizer);

        if (!tokenizer.advance())
            return;

        directives = parseDirectivesConstOpt(tokenizer);
        if (directives == null)
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
        ScalarTypeDefinition that = (ScalarTypeDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(directives, that.directives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, directives);
    }

    @Override
    public String toString() {
        return "ScalarTypeDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }
}
