package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.Objects;

public class NamedType extends Type {
    String name;
    boolean nullable;

    public NamedType() {
    }

    public NamedType(String name) {
        this.name = name;
    }

    public NamedType(String name, boolean nullable) {
        this.name = name;
        this.nullable = nullable;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        name = tokenizer.string();
        if (!tokenizer.advance())
            return;

        if (tokenizer.type() == Tokenizer.Type.PUNCTUATOR && tokenizer.punctuator() == '!') {
            nullable = true;
        } else {
            tokenizer.revert();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NamedType namedType = (NamedType) o;
        return nullable == namedType.nullable && Objects.equals(name, namedType.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, nullable);
    }

    @Override
    public String toString() {
        return "NamedType{" +
                "name='" + name + '\'' +
                ", nullable=" + nullable +
                '}';
    }

    public boolean isNullable() {
        return nullable;
    }
}
