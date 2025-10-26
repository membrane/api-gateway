package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.node.*;

import java.util.*;

public class SchemaString extends SchemaObject {

    private List<String> enumeration = new ArrayList<>();

    public SchemaString(String name) {
        super(name);
        type("string");
    }

    public SchemaString enumeration(List<String> enumeration) {
        this.enumeration = enumeration;
        return this;
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        super.json(node);
        if (!enumeration.isEmpty()) {
            ArrayNode enumNode = jnf.arrayNode();
            enumeration.forEach(enumNode::add);
            node.set("enum", enumNode);
        }
        return node;
    }
}
