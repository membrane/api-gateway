package com.predic8.membrane.core.graphql.blacklist.filters;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.model.*;

import java.util.List;

@MCElement(name = "introspection")
public class IntrospectionFilter implements GraphQLFeatureFilter {

    @Override
    public void filter(ExecutableDocument document) throws GraphQLOverHttpValidationException {
        document.getOperationDefinitions().forEach(operation -> {
            checkSelections(operation.getSelections());
        });
    }

    private void checkSelections(List<Selection> selections) throws GraphQLOverHttpValidationException {
        selections.forEach(selection -> {
            if (selection instanceof Field f) {
                if (f.getName().startsWith("__")) {
                    throw new GraphQLOverHttpValidationException("Introspection queries are not permitted.");
                }
                checkSelections(f.getSelections());
            } else if (selection instanceof InlineFragment) {
                checkSelections(((InlineFragment) selection).getSelections());
            }
        });
    }
}