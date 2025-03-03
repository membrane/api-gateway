package com.predic8.membrane.core.graphql.blacklist;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.blacklist.filters.GraphQLFeatureFilter;
import com.predic8.membrane.core.graphql.model.ExecutableDocument;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@MCElement(name = "disallow")
public class FeatureBlacklist {

    private List<GraphQLFeatureFilter> filters;

    public void checkFilters(@NotNull ExecutableDocument document) throws GraphQLOverHttpValidationException {
        filters.forEach(f -> f.filter(document));
    }

    @MCChildElement(allowForeign = true)
    public void setFilters(List<GraphQLFeatureFilter> filters) {
        this.filters = filters;
    }

    public List<GraphQLFeatureFilter> getFilters() {
        return filters;
    }
}