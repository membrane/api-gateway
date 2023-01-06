package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.security.InvalidParameterException;
import java.util.Objects;

public class OperationType {
    private String operation;

    public OperationType() {
    }

    public OperationType(String operation) {
        this.operation = operation;
        if (!is(operation))
            throw new InvalidParameterException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationType that = (OperationType) o;
        return Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation);
    }

    @Override
    public String toString() {
        return "OperationType{" +
                "operation='" + operation + '\'' +
                '}';
    }

    public void parse(Tokenizer tokenizer) throws ParsingException {
        operation = tokenizer.string();
        if (!is(operation))
            throw new ParsingException("Invalid OperationType.", tokenizer.position());
    }

    public static boolean is(String operation) {
        return operation.equals("query") || operation.equals("mutation") || operation.equals("subscription");
    }

    public String getOperation() {
        return operation;
    }
}
