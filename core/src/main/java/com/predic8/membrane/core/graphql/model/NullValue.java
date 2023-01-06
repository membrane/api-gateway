package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.util.Objects;

public class NullValue implements Value {
    public NullValue() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String toString() {
        return "NullValue{" +
                '}';
    }

    public void parse(Tokenizer tokenizer) throws ParsingException {
        if (!tokenizer.string().equals("null"))
            throw new ParsingException("Expected 'null'.", tokenizer.position());
    }

}
