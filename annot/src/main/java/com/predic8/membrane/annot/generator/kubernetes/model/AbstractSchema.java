package com.predic8.membrane.annot.generator.kubernetes.model;

import java.util.*;

import static java.util.stream.Collectors.joining;

public abstract class AbstractSchema<T extends AbstractSchema<T>> implements ISchema {

    protected String name;

    protected boolean required = false;

    // Properties to be copied 1:1 to the JSON schema, e.g. "type": "string"
    protected final Map<String, Object> attributes = new LinkedHashMap<>();

    public AbstractSchema() {
    }

    public AbstractSchema(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }


    public T attribute(String key, Object value) {
        addAttribute(key, value);
        return self();
    }

    public T name(String name) {
        this.name = name;
        return self();
    }

    public T type(String type) {
        addAttribute("type", type);
        return self();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append("\"").append(name).append("\":");
        }
        sb.append("{");
        String attrs = attributes.entrySet().stream()
                .map(SchemaUtils::entryToJson)
                .collect(joining(","));
        sb.append(attrs);
        sb.append("}");
        return sb.toString();
    }
}
