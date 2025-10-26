package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;

public class AnyOf extends SchemaObject {

    private final List<SchemaObject> anyOfs;

    AnyOf(List<SchemaObject> anyOfs) {
        super(null);
        this.anyOfs = anyOfs;
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        return jnf.objectNode().set("anyOf", getAnyNode());
    }

    private ArrayNode getAnyNode() {
        ArrayNode list = JsonNodeFactory.instance.arrayNode();
        for (SchemaObject anyOf : anyOfs) {
            list.add(anyOf.json(jnf.objectNode()));
        }
        return list;
    }
}
