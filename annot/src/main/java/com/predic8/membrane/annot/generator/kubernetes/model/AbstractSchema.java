package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.node.*;

public abstract class AbstractSchema<T extends AbstractSchema<T>> implements ISchema {

    protected static final JsonNodeFactory jnf = JsonNodeFactory.instance;

    protected String name;
    protected String type;
    protected String description;

    protected boolean required = false;

    public AbstractSchema() {
    }

    public AbstractSchema(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    @Override
    public String getName() {
        return name;
    }

    public T description(String description) {
        this.description = description;
        return self();
    }

    public boolean isRequired() {
        return required;
    }

    public T required(boolean b) {
        required = b;
        return self();
    }

    public T name(String name) {
        this.name = name;
        return self();
    }

    public T type(String type) {
        this.type = type;
        return self();
    }

    public boolean isObject() {
        return "object".equals(type);
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        if (type != null)
            node.put("type", type);
        return node;
    }
}