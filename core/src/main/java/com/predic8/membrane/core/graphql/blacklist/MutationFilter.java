package com.predic8.membrane.core.graphql.blacklist;

import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.model.Selection;

public interface MutationFilter {

    void filter(Selection ed) throws GraphQLOverHttpValidationException;
}
