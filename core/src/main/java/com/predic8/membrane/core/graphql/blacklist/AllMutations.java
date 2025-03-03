package com.predic8.membrane.core.graphql.blacklist;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.model.Selection;

@MCElement(name = "allMutations")
public class AllMutations implements MutationFilter {

    public void filter(Selection ed) throws GraphQLOverHttpValidationException {
        throw new GraphQLOverHttpValidationException("No mutations are permitted.");
    }
}
