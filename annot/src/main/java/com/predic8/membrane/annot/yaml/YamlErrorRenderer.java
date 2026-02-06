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

import com.fasterxml.jackson.core.*;
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
     * @return YAML string with "^^^ ERROR" marker at the target location
     */
    public static String renderErrorReport(ParsingContext pc) throws JsonProcessingException {

        JsonNode node = pc.getNode();
        String jsonPath = pc.path();
        String key = pc.getKey();

        System.out.println("node = " + node + ", jsonPath = " + jsonPath + ", key = " + key);

        // Create a working copy
        JsonNode workingCopy = node.deepCopy();

        // Use JSONPath to navigate to the parent
        DocumentContext ctx = JsonPath.using(JSON_PATH_CONFIG).parse(workingCopy);

        // Read the parent object or array
        Object parent = ctx.read(jsonPath);

        if (parent == null) {
            throw new IllegalArgumentException("Parent path not found: " + jsonPath);
        }

        if (parent instanceof ObjectNode parentNode) {
            // Handle object: key is a field name
            JsonNode targetNode = parentNode.get(key);
            if (targetNode == null) {
                //throw new IllegalArgumentException("Key '" + key + "' not found in parent at: " + jsonPath);
                targetNode = node; // ?
            }

            // Create wrapper that contains the field
            ObjectNode wrapper = JSON_MAPPER.createObjectNode();
            ObjectNode innerObject = JSON_MAPPER.createObjectNode();
            innerObject.set(key, targetNode.deepCopy());
            wrapper.set(MARKER_KEY, innerObject);

            // Remove the original field
            parentNode.remove(key);

            // Add the wrapper
            parentNode.setAll(wrapper);

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

            JsonNode targetNode = parentArray.get(index);

            // Wrap the entire element
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

    }

    /**
     * Replaces the marker structure in YAML with an error indicator.
     */
    private static String replaceMarkerWithError(String yaml, String errorKey) {
        String[] lines = yaml.split("\n");
        StringBuilder result = new StringBuilder();

        result.append("Config:\n");
        result.append(CYAN());

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // Look for __ERROR_MARKER__: line (with or without array dash)
            if (trimmed.equals(MARKER_KEY + ":") || trimmed.equals("- " + MARKER_KEY + ":")) {
                int markerIndent = getIndentation(line);
                boolean isArrayElement = trimmed.startsWith("- ");

                i++; // Move to the first content line inside the marker

                if (i >= lines.length) {
                    continue;
                }

                String firstContentLine = lines[i];
                int contentIndent = getIndentation(firstContentLine);

                // Determine if the content is a single field or complex object
                String firstContentTrimmed = firstContentLine.trim();

                // Output the first line
                if (isArrayElement) {
                    // This is an array element, so add the dash
                    result.append(" ".repeat(markerIndent))
                            .append("- ")
                            .append(firstContentTrimmed)
                            .append("\n");
                } else {
                    // Regular object field
                    result.append(" ".repeat(markerIndent))
                            .append(firstContentTrimmed)
                            .append("\n");
                }

                i++; // Move to next line

                // Copy remaining nested content (if any) with adjusted indentation
                while (i < lines.length && getIndentation(lines[i]) > contentIndent) {
                    String nestedLine = lines[i];
                    int nestedIndent = getIndentation(nestedLine);

                    // Calculate relative indentation from the content
                    int relativeIndent = nestedIndent - contentIndent;
                    int newIndent = markerIndent + (isArrayElement ? 2 : 0) + relativeIndent;

                    result.append(" ".repeat(newIndent))
                            .append(nestedLine.trim())
                            .append("\n");
                    i++;
                }

                // Add error marker - align with content (add 2 spaces for array dash)
                int errorIndent = markerIndent + (isArrayElement ? 2 : 0);
                result.append(" ".repeat(errorIndent))
                        .append(ERROR_POINTER);
                result.append(CYAN());
                i--; // Back up one since loop will increment
                continue;
            }

            // No marker found, copy line as-is
            result.append(line).append("\n");
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