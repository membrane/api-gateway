package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public class SchemaRef extends SchemaObject {

    private String ref;

    SchemaRef(String name) {
        super(name);
    }

    public SchemaRef ref(String ref) {
        this.ref = ref;
        return this;
    }

    public ObjectNode json(ObjectNode node) {
//        ObjectNode r = jnf.objectNode();
        node.put("$ref", ref);
//        node.put(name, r);
        return node;
    }

}
