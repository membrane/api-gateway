package com.predic8.membrane.core.graphql.blacklist;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.graphql.GraphQLOverHttpValidationException;
import com.predic8.membrane.core.graphql.model.Field;
import com.predic8.membrane.core.graphql.model.Selection;

@MCElement(name = "mutation")
public class Mutation implements MutationFilter {

    private String name;

    public void filter(Selection ed) throws GraphQLOverHttpValidationException {
        System.out.println("WOW!");
        if (((Field) ed).getName().equals(name)) {
            throw new GraphQLOverHttpValidationException("Mutation \"" + name + "\" not permitted.");
        }
    }

    @MCAttribute
    public void setName(String name) {this.name = name;}

    public String getName() {return name;}
}