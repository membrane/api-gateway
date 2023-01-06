package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.Tokenizer;

import java.util.Objects;

public class StringValue implements Value {
    String value;

    public StringValue() {
    }

    public StringValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringValue that = (StringValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "StringValue{" +
                "value='" + value + '\'' +
                '}';
    }

    public void parse(Tokenizer tokenizer) {
        value = tokenizer.string();
    }
}
