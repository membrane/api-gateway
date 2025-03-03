package com.predic8.membrane.core.graphql.blacklist.filters;

import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.model.ExecutableDocument;

public interface GraphQLFeatureFilter {
    void filter(ExecutableDocument document) throws GraphQLOverHttpValidationException;
}