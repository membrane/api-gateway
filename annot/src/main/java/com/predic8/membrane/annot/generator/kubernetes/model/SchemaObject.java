package com.predic8.membrane.annot.generator.kubernetes.model;

import java.util.*;
import java.util.stream.Collectors;

public class SchemaObject implements ISchema {

    private final String name;
    private boolean required;

    private String description;

    private final Map<String, Object> attributes = new HashMap<>();
    private final List<SchemaObject> properties = new ArrayList<>();

    public SchemaObject(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "\"" + name + "\": {" +
                attributes.entrySet().stream()
                        .map(this::entryToJson)
                        .collect(Collectors.joining(",")) +
                printProperties() +
                "}"
                ;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    private String printRequired() {
        String req = properties.stream()
                .filter(SchemaObject::isRequired)
                .map(so -> "\"" + so.name + "\"")
                .collect(Collectors.joining(","));

        if (req.isEmpty())
            return "";

        return ",\"required\":[" + req + "]";
    }

    private String printProperties() {
        if (properties.isEmpty())
            return "";

        return ",\"properties\": {" +
                properties.stream().map(SchemaObject::toString).collect(Collectors.joining(",")) +
                "}" +
                printRequired()
                ;
    }

    public String entryToJson(Map.Entry<String, Object> entry) {
        if (entry.getValue() instanceof String) {
            return "\"" + entry.getKey() + "\": \"" + entry.getValue() + "\"";
        }
        return "\"" + entry.getKey() + "\": " + entry.getValue();
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public void addProperty(SchemaObject so) {
        properties.add(so);
    }

    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}
