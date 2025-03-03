package com.predic8.membrane.core.graphql.blacklist.filters;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.model.ExecutableDocument;

import static com.predic8.membrane.core.graphql.GraphQLoverHttpValidator.getMutationOperations;

@MCElement(name = "mutations")
public class AllMutationsFilter implements GraphQLFeatureFilter {

    @Override
    public void filter(ExecutableDocument document) throws GraphQLOverHttpValidationException {
        if (getMutationOperations(document.getExecutableDefinitions()).findAny().isPresent()) {
            throw new GraphQLOverHttpValidationException("No mutations are permitted.");
        }
    }
}