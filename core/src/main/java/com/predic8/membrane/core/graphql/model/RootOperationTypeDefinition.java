package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.*;

public class RootOperationTypeDefinition {
    private OperationType operationType;
    private NamedType type;

    public RootOperationTypeDefinition() {
    }

    public RootOperationTypeDefinition(OperationType operationType, NamedType type) {
        this.operationType = operationType;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RootOperationTypeDefinition that = (RootOperationTypeDefinition) o;
        return Objects.equals(operationType, that.operationType) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, type);
    }

    @Override
    public String toString() {
        return "RootOperationDefinition{" +
                "operationType=" + operationType +
                ", type=" + type +
                '}';
    }

    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        operationType = new OperationType();
        operationType.parse(tokenizer);

        tokenizer.mustAdvance();
        if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != ':')
            throw new ParsingException("Expected ':'.", tokenizer.position());

        tokenizer.mustAdvance();
        if (tokenizer.type() != NAME)
            throw new ParsingException("Expected type.", tokenizer.position());

        type = new NamedType();
        type.parse(tokenizer);
    }

}
