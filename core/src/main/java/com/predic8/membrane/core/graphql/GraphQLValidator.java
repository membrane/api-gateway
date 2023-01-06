package com.predic8.membrane.core.graphql;

import com.predic8.membrane.core.graphql.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * So far, this is not a complete validator.
 */
public class GraphQLValidator {
    public List<String> validate(ExecutableDocument ed) {
        List<String> res = new ArrayList<>();
        if (!ed.getExecutableDefinitions().stream()
                .filter(ed2 -> ed2 instanceof OperationDefinition)
                .map(ed2 -> ((OperationDefinition)ed2).getName())
                .filter(Objects::nonNull)
                .allMatch(new HashSet<>()::add))
            res.add("Names of operationDefinitions must be unique.");

        if (ed.getExecutableDefinitions().stream()
                .filter(ed2 -> ed2 instanceof OperationDefinition)
                .map(ed2 -> ((OperationDefinition)ed2).getName())
                .anyMatch(Objects::isNull) &&
            ed.getExecutableDefinitions().stream()
                    .filter(ed2 -> ed2 instanceof OperationDefinition)
                    .count() > 1)
            res.add("If there is an anonymous operationDefinition, there may not be other operationDefinitions.");

        if (!ed.getExecutableDefinitions().stream()
                .filter(ed2 -> ed2 instanceof FragmentDefinition)
                .map(ed2 -> ((FragmentDefinition)ed2).getName())
                .filter(Objects::nonNull)
                .allMatch(new HashSet<>()::add))
            res.add("Names of fragmentDefinitions must be unique.");

        return res;
    }
}
