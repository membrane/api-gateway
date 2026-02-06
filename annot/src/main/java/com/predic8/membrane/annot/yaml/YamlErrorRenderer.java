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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.dataformat.yaml.*;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.*;
import com.jayway.jsonpath.spi.mapper.*;

import static com.predic8.membrane.common.TerminalColorsMini.*;

/**
 * Utility for rendering error reports in YAML format with visual error markers.
 */
public class YamlErrorRenderer {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            YAMLFactory.builder()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .build()
    );

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    private static final String MARKER_KEY = "__ERROR_MARKER__";
    public static final String ERROR_POINTER = RED() + "^^^ ERROR\n" + CYAN();

    /**
     * Renders a YAML representation of the JSON node with an error marker at the specified location.
     *
     * @param node     the root JSON node
     * @param jsonPath the JSONPath to the parent object or array (e.g., "$.api.flow[0].request[0].request")
     * @param key      the field name (for objects) or index (for arrays, e.g., "1") to mark
     * @return YAML string with "^^^ ERROR" marker at the target location
     */
    public static String renderErrorReport(JsonNode node, String jsonPath, String key) {
        try {
            // Create a working copy
            JsonNode workingCopy = node.deepCopy();

            // Use JSONPath to navigate to the parent
            DocumentContext ctx = JsonPath.using(JSON_PATH_CONFIG).parse(workingCopy);

            // Read the parent object or array
            Object parent = ctx.read(jsonPath);

            if (parent == null) {
                throw new IllegalArgumentException("Parent path not found: " + jsonPath);
            }

            JsonNode targetNode;

            if (parent instanceof ObjectNode parentNode) {
                // Handle object: key is a field name
                targetNode = parentNode.get(key);
                if (targetNode == null) {
                    throw new IllegalArgumentException("Key '" + key + "' not found in parent at: " + jsonPath);
                }

                // Wrap the target field in a marker structure
                ObjectNode wrapper = JSON_MAPPER.createObjectNode();
                wrapper.set(MARKER_KEY, targetNode.deepCopy());

                // Replace the original field with the wrapped version
                parentNode.set(key, wrapper);

            } else if (parent instanceof ArrayNode parentArray) {
                // Handle array: key is an index
                int index;
                try {
                    index = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Key '" + key + "' is not a valid array index for array at: " + jsonPath);
                }

                if (index < 0 || index >= parentArray.size()) {
                    throw new IllegalArgumentException("Index " + index + " out of bounds for array at: " + jsonPath);
                }

                targetNode = parentArray.get(index);

                // Wrap the target element in a marker structure
                ObjectNode wrapper = JSON_MAPPER.createObjectNode();
                wrapper.set(MARKER_KEY, targetNode.deepCopy());

                // Replace the original element with the wrapped version
                parentArray.set(index, wrapper);

            } else {
                throw new IllegalArgumentException("Parent is neither an object nor an array: " + jsonPath);
            }

            // Convert to YAML
            String yaml = YAML_MAPPER.writeValueAsString(workingCopy);

            // Replace marker with error indicator
            return replaceMarkerWithError(yaml, key);

        } catch (Exception e) {
            throw new RuntimeException("Failed to render error report: " + e.getMessage(), e);
        }
    }

    /**
     * Replaces the marker structure in YAML with an error indicator.
     */
    private static String replaceMarkerWithError(String yaml, String errorKey) {
        String[] lines = yaml.split("\n");
        StringBuilder result = new StringBuilder();

        result.append("Configuration:\n");
        result.append(CYAN());

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Look for the error key
            if (line.trim().startsWith(errorKey + ":")) {
                int keyIndent = getIndentation(line);

                // Check if next line contains the marker
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1];
                    String trimmedNext = nextLine.trim();

                    if (trimmedNext.startsWith(MARKER_KEY + ":")) {
                        // Found the marker - extract the value
                        String markerLine = trimmedNext.substring(MARKER_KEY.length() + 1).trim();

                        if (!markerLine.isEmpty()) {
                            // Inline value: __ERROR_MARKER__: "Df"
                            result.append(" ".repeat(keyIndent))
                                    .append(errorKey)
                                    .append(": ")
                                    .append(markerLine)
                                    .append("\n");
                            result.append(" ".repeat(keyIndent))
                                    .append(ERROR_POINTER);
                            i++; // Skip the marker line
                        } else {
                            // Multi-line value: __ERROR_MARKER__: followed by nested content
                            result.append(" ".repeat(keyIndent))
                                    .append(errorKey)
                                    .append(":\n");

                            int markerIndent = getIndentation(nextLine);
                            i += 2; // Skip errorKey line and marker line

                            // Copy all nested content
                            while (i < lines.length && getIndentation(lines[i]) > markerIndent) {
                                String contentLine = lines[i];
                                int contentIndent = getIndentation(contentLine);
                                // Adjust indentation: remove marker's extra indent
                                int newIndent = keyIndent + (contentIndent - markerIndent);
                                result.append(" ".repeat(newIndent))
                                        .append(contentLine.trim())
                                        .append("\n");
                                i++;
                            }

                            result.append(" ".repeat(keyIndent))
                                    .append(ERROR_POINTER);
                            i--; // Back up one since loop will increment
                        }
                        continue;
                    }
                }

                // No marker found, copy line as-is
                result.append(line).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        result.append(RESET());

        return result.toString().trim();
    }

    private static int getIndentation(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                indent++;
            } else {
                break;
            }
        }
        return indent;
    }
}