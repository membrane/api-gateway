package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.Tokenizer;

import java.util.Objects;

public class IntValue implements Value {
    int value;

    public IntValue() {
    }

    public IntValue(int value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntValue intValue = (IntValue) o;
        return value == intValue.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "IntValue{" +
                "value=" + value +
                '}';
    }

    public void parse(Tokenizer tokenizer) {
        value = tokenizer.integer();
    }

}
