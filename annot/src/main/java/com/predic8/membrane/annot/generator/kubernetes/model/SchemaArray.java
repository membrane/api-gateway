package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.node.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.ARRAY;

public class SchemaArray extends AbstractSchema<SchemaArray> {

    AbstractSchema items;

    SchemaArray(String name) {
        super(name);
        type(ARRAY);
    }

    public SchemaArray items(AbstractSchema items) {
        this.items = items;
        return this;
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        super.json(node);
        if (items != null) {
            node.set("items", items.json(jnf.objectNode()));
        }
        return node;
    }

    @Override
    public boolean isObject() {
        return false;
    }
}
