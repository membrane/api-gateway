/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION;
import static tools.jackson.dataformat.yaml.YAMLFactory.builder;


/**
 * A utility class for parsing YAML content into JSON nodes while preserving location information.
 * This class leverages the Jackson library for YAML parsing and allows mapping each JSON node
 * to its corresponding source location within the parsed YAML content.
 *
 * The class maintains an internal map that stores the locations of JSON nodes using their instance
 * references. This is useful for tracking structural entities, such as objects and arrays, in relation
 * to their positions in the source document.
 *
 * Implementation note: The 'normal' ObjectMapper JSON/YAML parser returns the *same* JsonNode instance,
 * for example when 'false' occurs multiple times in the input. This class needs to distinguish between
 * these instances.
 */
public class JsonLocationMap {

    private static final YAMLFactory yamlFactory = builder().enable(STRICT_DUPLICATE_DETECTION).build();

    // Use format-specific mapper instead of new ObjectMapper(YAMLFactory)
    private static final ObjectMapper om = YAMLMapper.builder(yamlFactory).build();

    // We use IdentityHashMap because different nodes might have identical content
    // but we want to track the specific instance in the tree.
    private final Map<JsonNode, TokenStreamLocation> locationMap = new IdentityHashMap<>();

    public Map<JsonNode, TokenStreamLocation> getLocationMap() {
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

        TokenStreamLocation location = parser.currentLocation();

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