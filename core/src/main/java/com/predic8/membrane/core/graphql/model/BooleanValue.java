package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.util.Objects;

public class BooleanValue implements Value {
    boolean value;

    public BooleanValue() {
    }

    public BooleanValue(boolean value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BooleanValue booleanValue = (BooleanValue) o;
        return value == booleanValue.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "BooleanValue{" +
                "value=" + value +
                '}';
    }

    public void parse(Tokenizer tokenizer) throws ParsingException {
        String s = tokenizer.string();
        if (!s.equals("true") && !s.equals("false"))
            throw new ParsingException("Expected 'true' or 'false'.", tokenizer.position());
        value = Boolean.parseBoolean(s);
    }

}
