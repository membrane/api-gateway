package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.OBJECT;

public class Schema extends SchemaObject {

    private final List<ISchema> definitions = new ArrayList<>();
    private String version;

    Schema(String name) {
        super(name);
        type = OBJECT;
    }

    public Schema definition(ISchema definition) {
        definitions.add(definition);
        return this;
    }

    public Schema version(String version) {
        this.version = version;
        return this;
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        node.put("$id", "https://membrane-soa.org/%s.schema.json".formatted(name));
        node.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        node.put("title", name);
        if (version != null && !version.isBlank()) {
            node.put("version", version);
        }
        super.json(node);
        if (!definitions.isEmpty()) {
            ObjectNode defs = jnf.objectNode();
            for (ISchema def : definitions) {
                defs.set(def.getName(), def.json(jnf.objectNode()));
            }
            node.set("$defs", defs);
        }
        return node;
    }
}