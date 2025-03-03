package com.predic8.membrane.core.graphql.blacklist.filters;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.model.ExecutableDocument;
import com.predic8.membrane.core.graphql.model.Field;
import com.predic8.membrane.core.graphql.model.Selection;

import java.util.List;

import static com.predic8.membrane.core.graphql.GraphQLoverHttpValidator.getMutationOperations;

@MCElement(name = "mutation")
public class MutationFilter implements GraphQLFeatureFilter {

    private String name;

    @Override
    public void filter(ExecutableDocument document) throws GraphQLOverHttpValidationException {
        getMutationOperations(document.getExecutableDefinitions()).forEach(mutation ->
                checkMutationSelections(mutation.getSelections())
        );
    }

    private void checkMutationSelections(List<Selection> selections) throws GraphQLOverHttpValidationException {
        selections.forEach(selection -> {
            if (selection instanceof Field field) {
                if (field.getName().equals(name)) {
                    throw new GraphQLOverHttpValidationException("Mutation \"" + name + "\" not permitted.");
                }
            }
        });
    }

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}