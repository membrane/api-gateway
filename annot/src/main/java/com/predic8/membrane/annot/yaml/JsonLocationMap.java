package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION;
import static com.fasterxml.jackson.dataformat.yaml.YAMLFactory.builder;

public class JsonLocationMap {

    private static final YAMLFactory yamlFactory = builder().enable(STRICT_DUPLICATE_DETECTION).build();
    private static final ObjectMapper om = new ObjectMapper(yamlFactory);

    // We use IdentityHashMap because different nodes might have identical content
    // but we want to track the specific instance in the tree.
    private final Map<JsonNode, JsonLocation> locationMap = new IdentityHashMap<>();

    public Map<JsonNode, JsonLocation> getLocationMap() {
        return locationMap;
    }

    public List<JsonNode> parseWithLocations(String content) throws IOException {
        List<JsonNode> res = new ArrayList<>();
        try (JsonParser parser = yamlFactory.createParser(content)) {
            while (!parser.isClosed()) {
                res.add(parseRecursive(parser, om.getNodeFactory()));
                parser.nextToken();
            }
        }
        return res;
    }

    private JsonNode parseRecursive(JsonParser parser, JsonNodeFactory nodeFactory) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            token = parser.nextToken();
        }

        if (token == null) return null;

        JsonLocation location = parser.currentLocation();

        switch (token) {
            case START_OBJECT:
                ObjectNode objectNode = nodeFactory.objectNode();
                // Record location for this object
                locationMap.put(objectNode, location);
                
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();
                    JsonNode child = parseRecursive(parser, nodeFactory);
                    objectNode.set(fieldName, child);
                }
                return objectNode;

            case START_ARRAY:
                ArrayNode arrayNode = nodeFactory.arrayNode();
                // Record location for this array
                locationMap.put(arrayNode, location);
                
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode child = parseRecursive(parser, nodeFactory);
                    arrayNode.add(child);
                }
                return arrayNode;

            default:
                return getValueNode(parser, nodeFactory);
        }
    }

    private JsonNode getValueNode(JsonParser parser, JsonNodeFactory nodeFactory) throws IOException {
        JsonToken token = parser.currentToken();
        JsonNode node = switch (token) {
            case VALUE_NUMBER_INT -> nodeFactory.numberNode(parser.getBigIntegerValue());
            case VALUE_NUMBER_FLOAT -> nodeFactory.numberNode(parser.getDecimalValue());
            case VALUE_TRUE -> nodeFactory.booleanNode(true);
            case VALUE_FALSE -> nodeFactory.booleanNode(false);
            case VALUE_NULL -> nodeFactory.nullNode();
            default -> nodeFactory.textNode(parser.getText());
        };
        // Note: Locations for boolean/null might behave unexpectedly due to caching
        locationMap.put(node, parser.currentLocation());
        return node;
    }
}