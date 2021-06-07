package com.predic8.membrane.annot.generator.kubernetes.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Schema implements ISchema {

    private final String name;

    private final List<SchemaObject> definitions = new ArrayList<>();
    private final List<SchemaObject> properties = new ArrayList<>();
    private final List<SchemaObject> interceptors = new ArrayList<>();

    public Schema(String name) {
        this.name = name;
    }

    public void addInterceptor(SchemaObject interceptor) {
        interceptors.add(interceptor);
    }

    public void addDefinition(SchemaObject definition) {
        definitions.add(definition);
    }

    @Override
    public void addProperty(SchemaObject property) {
        properties.add(property);
    }

    private String printDefinitions() {
        if (definitions.isEmpty())
            return "";

        return "\"definitions\":{" + definitions.stream()
                .map(Objects::toString)
                .collect(Collectors.joining(",")) +
                "}";
    }

    private String printProperties() {
        if (properties.isEmpty())
            return "";

        return "\"properties\":{\"spec\":{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{" +
                properties.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining(",")) +
                printInterceptors() +
                "}}}";
    }

    private String printInterceptors() {
        if (interceptors.isEmpty())
            return "";

        return ",\"interceptors\":{\"type\": \"array\"," +
                "\"additionalItems\": false,\"items\":{" +
                "\"type\":\"object\",\"additionalProperties\": false,\"properties\":{" +
                interceptors.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining(",")) +
                "}}}";
    }

    private String checkTrailingCommaNeed() {
        boolean hasDefs = !definitions.isEmpty();
        boolean hasProps = !properties.isEmpty();
        return ((hasDefs && !hasProps) || (!hasDefs && hasProps)) ? "" : ",";
    }

    private String printRequired() {
        String required = properties.stream()
                .filter(SchemaObject::isRequired)
                .map(so -> "\"" + so.getName() + "\"")
                .collect(Collectors.joining(","));

        if (required.isEmpty())
            return "";

        return ",\"required\":[" + required + "]";
    }

    @Override
    public String toString() {
        return "{" +
                "\"id\": \"https://membrane-soa.org/" + name.toLowerCase() + ".schema.json\"," +
                "\"$schema\": \"https://json-schema.org/draft-04/schema#\"," +
                "\"title\": \"" + name + "\"," +
                "\"type\": \"object\"," +
                printDefinitions() +
                checkTrailingCommaNeed() +
                printProperties() +
                printRequired() +
                "}"
                ;
    }
}
