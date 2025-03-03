package com.predic8.membrane.core.graphql.blacklist;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.model.OperationDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@MCElement(name = "disallow")
public class FeatureBlacklist {

    private List<MutationFilter> filters;

    public void checkFilters(@NotNull Stream<OperationDefinition> ed) throws GraphQLOverHttpValidationException {
        ed.map(OperationDefinition::getSelections).flatMap(Collection::stream).forEach(selection ->
                filters.forEach(f -> f.filter(selection))
        );
    }

    @MCChildElement(allowForeign = true)
    public void setFilters(List<MutationFilter> filters) {this.filters = filters;}

    public List<MutationFilter> getFilters() {return filters;}
}
