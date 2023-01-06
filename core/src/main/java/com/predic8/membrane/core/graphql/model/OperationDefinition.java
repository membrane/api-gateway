package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.*;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class OperationDefinition extends ExecutableDefinition {
    OperationType operationType;
    String name;
    List<VariableDefinition> variableDefinitions;
    List<Directive> directives;
    List<Selection> selections;

    public OperationDefinition() {
    }

    public OperationDefinition(OperationType operationType, String name, List<VariableDefinition> variableDefinitions, List<Directive> directives, List<Selection> selections) {
        this.operationType = operationType;
        this.name = name;
        this.variableDefinitions = variableDefinitions;
        this.directives = directives;
        this.selections = selections;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        if (tokenizer.type() == Tokenizer.Type.NAME && OperationType.is(tokenizer.string())) {
            operationType = new OperationType();
            operationType.parse(tokenizer);

            tokenizer.mustAdvance();
            if (tokenizer.type() == Tokenizer.Type.NAME) {
                name = tokenizer.string();
                tokenizer.mustAdvance();
            }

            variableDefinitions = parseVariableDefinitionsOpt(tokenizer);
            if (variableDefinitions != null)
                tokenizer.mustAdvance();

            directives = parseDirectivesOpt(tokenizer);
            if (directives != null)
                tokenizer.mustAdvance();

            if (tokenizer.type() != Tokenizer.Type.PUNCTUATOR || tokenizer.punctuator() != '{')
                throw new ParsingException("Expected '{'.", tokenizer.position());
        } else {
            if (tokenizer.type() != Tokenizer.Type.PUNCTUATOR || tokenizer.punctuator() != '{')
                throw new ParsingException("Expected 'query', 'mutation', 'subscription' or '{'.", tokenizer.position());
        }

        selections = parseSelectionSetOpt(tokenizer); // this will always find a selection set, because we are at the '{'.
        // no need to revert, since selections cannot be null
    }

    public static List<VariableDefinition> parseVariableDefinitionsOpt(Tokenizer t) throws IOException, ParsingException {
        if (t.type() != PUNCTUATOR || t.punctuator() != '(')
            return null;
        List<VariableDefinition> res = new ArrayList<>();
        t.mustAdvance();
        do {
            VariableDefinition a = new VariableDefinition();
            a.parse(t);
            res.add(a);

            t.mustAdvance();
        } while (t.type() != PUNCTUATOR || t.punctuator() != ')');
        return res;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationDefinition that = (OperationDefinition) o;
        return Objects.equals(operationType, that.operationType) && Objects.equals(name, that.name) && Objects.equals(variableDefinitions, that.variableDefinitions) && Objects.equals(directives, that.directives) && Objects.equals(selections, that.selections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, name, variableDefinitions, directives, selections);
    }

    @Override
    public String toString() {
        return "OperationDefinition{" +
                "operationType=" + operationType +
                ", name='" + name + '\'' +
                ", variableDefinitions=" + variableDefinitions +
                ", directives=" + directives +
                ", selections=" + selections +
                '}';
    }

    public String getName() {
        return name;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public List<Selection> getSelections() {
        return selections;
    }
}
