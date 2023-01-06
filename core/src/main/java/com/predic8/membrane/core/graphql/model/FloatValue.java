package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.Tokenizer;

import java.util.Objects;

public class FloatValue implements Value {
    double value;

    public FloatValue() {
    }

    public FloatValue(double value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FloatValue that = (FloatValue) o;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "FloatValue{" +
                "value=" + value +
                '}';
    }

    public void parse(Tokenizer tokenizer) {
        value = tokenizer.float_();
    }

}
