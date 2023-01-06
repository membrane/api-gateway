package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.parseName;
import static com.predic8.membrane.core.graphql.ParserUtil.parseValueQuestionConst;

public class ObjectField {
    private String name;
    private Value value;

    public ObjectField() {
    }

    public ObjectField(String name, Value value) {
        this.name = name;
        this.value = value;
    }

    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.revert();
        name = parseName(tokenizer);
        tokenizer.mustAdvance();
        if (tokenizer.type() != Tokenizer.Type.PUNCTUATOR || tokenizer.punctuator() != ':')
            throw new ParsingException("Expected ':'.", tokenizer.position());
        tokenizer.mustAdvance();
        value = parseValueQuestionConst(tokenizer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectField field = (ObjectField) o;
        return Objects.equals(name, field.name) && Objects.equals(value, field.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return "ObjectField{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
