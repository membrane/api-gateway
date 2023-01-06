package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;
import com.predic8.membrane.core.graphql.ParserUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ListValue implements Value {

    List<Value> values = new ArrayList<>();

    public ListValue() {
    }

    public ListValue(List<Value> values) {
        this.values = values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListValue listValue = (ListValue) o;
        return Objects.equals(values, listValue.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "ListValue{" +
                "values=" + values +
                '}';
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        while(tokenizer.type() != Tokenizer.Type.PUNCTUATOR || tokenizer.punctuator() != ']') {
            Value value = ParserUtil.parseValueQuestionConst(tokenizer);
            values.add(value);

            tokenizer.mustAdvance();
        }
    }
}
