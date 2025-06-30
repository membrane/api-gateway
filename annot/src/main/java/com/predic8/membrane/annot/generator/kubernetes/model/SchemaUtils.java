package com.predic8.membrane.annot.generator.kubernetes.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SchemaUtils {

    public static String entryToJson(Map.Entry<String, Object> entry) {
        if (entry.getValue() instanceof SchemaObject)
            return entry.getValue().toString();
        if (entry.getValue() instanceof String) {
            return "\"" + entry.getKey() + "\": \"" + entry.getValue() + "\"";
        }
        return "\"" + entry.getKey() + "\": " + entry.getValue();
    }

    public static String printRequired(List<SchemaObject> properties) {
        String req = properties.stream()
                .filter(SchemaObject::isRequired)
                .map(so -> "\"" + so.getName() + "\"")
                .collect(Collectors.joining(","));

        if (req.isEmpty())
            return "";

        return ",\"required\":[" + req + "]";
    }

}
