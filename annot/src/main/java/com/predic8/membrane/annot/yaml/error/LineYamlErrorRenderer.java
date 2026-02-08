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

package com.predic8.membrane.annot.yaml.error;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.dataformat.yaml.*;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.*;
import com.jayway.jsonpath.spi.mapper.*;
import com.predic8.membrane.annot.yaml.*;

import static com.predic8.membrane.common.TerminalColorsMini.*;

/**
 * Utility for rendering error reports in YAML format with line-based error markers.
 * Marked lines are prefixed with ">" at the left edge and colored red.
 * All lines are indented by 2 spaces from the left margin.
 */
public class LineYamlErrorRenderer {

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

    /**
     * Renders a YAML representation of the JSON node with line-based error markers.
     *
     * @return YAML string with ">" prefixes and red color for error lines
     */
    public static String renderErrorReport(ParsingContext pc) throws JsonProcessingException {

        JsonNode node = pc.getNode();
        String jsonPath = pc.path();
        String key = pc.getKey();

        // Create a working copy
        JsonNode workingCopy = node.deepCopy();

        // Use JSONPath to navigate
        DocumentContext ctx = JsonPath.using(JSON_PATH_CONFIG).parse(workingCopy);

        if (key == null) {
            // Mark the element at jsonPath itself
            if (jsonPath.equals("$")) {
                // Root element - wrap the entire document
                ObjectNode rootWrapper = JSON_MAPPER.createObjectNode();
                rootWrapper.set(MARKER_KEY, workingCopy.deepCopy());
                workingCopy = rootWrapper;
            } else {
                String parentPath = getParentPath(jsonPath);
                String lastSegment = getLastSegment(jsonPath);

                Object parent = ctx.read(parentPath);

                if (parent instanceof ObjectNode parentNode) {
                    JsonNode targetNode = parentNode.get(lastSegment);
                    if (targetNode == null) {
                        throw new IllegalArgumentException("Field '" + lastSegment + "' not found at: " + parentPath);
                    }

                    // Create a new ObjectNode preserving field order
                    ObjectNode newParent = JSON_MAPPER.createObjectNode();

                    // Copy all fields in order, wrapping the target
                    parentNode.fields().forEachRemaining(entry -> {
                        if (entry.getKey().equals(lastSegment)) {
                            // Wrap this field with marker
                            ObjectNode wrapper = JSON_MAPPER.createObjectNode();
                            ObjectNode innerObject = JSON_MAPPER.createObjectNode();
                            innerObject.set(lastSegment, entry.getValue().deepCopy());
                            wrapper.set(MARKER_KEY, innerObject);
                            newParent.setAll(wrapper);
                        } else {
                            newParent.set(entry.getKey(), entry.getValue());
                        }
                    });

                    // Replace parent's content with new ordered content
                    parentNode.removeAll();
                    parentNode.setAll(newParent);

                } else if (parent instanceof ArrayNode parentArray) {
                    int index = Integer.parseInt(lastSegment);
                    if (index < 0 || index >= parentArray.size()) {
                        throw new IllegalArgumentException("Index " + index + " out of bounds at: " + parentPath);
                    }

                    JsonNode targetNode = parentArray.get(index);
                    ObjectNode wrapper = JSON_MAPPER.createObjectNode();
                    wrapper.set(MARKER_KEY, targetNode.deepCopy());
                    parentArray.set(index, wrapper);

                } else {
                    throw new IllegalArgumentException("Parent is neither an object nor an array: " + parentPath);
                }
            }
        } else {
            // Mark a field/element within the parent at jsonPath
            Object parent = ctx.read(jsonPath);

            if (parent == null) {
                throw new IllegalArgumentException("Parent path not found: " + jsonPath);
            }

            if (parent instanceof ObjectNode parentNode) {
                JsonNode targetNode = parentNode.get(key);
                if (targetNode == null) {
                    throw new IllegalArgumentException("Key '" + key + "' not found in parent at: " + jsonPath);
                }

                // Create a new ObjectNode preserving field order
                ObjectNode newParent = JSON_MAPPER.createObjectNode();

                // Copy all fields in order, wrapping the target
                parentNode.fields().forEachRemaining(entry -> {
                    if (entry.getKey().equals(key)) {
                        // Wrap this field with marker
                        ObjectNode wrapper = JSON_MAPPER.createObjectNode();
                        ObjectNode innerObject = JSON_MAPPER.createObjectNode();
                        innerObject.set(key, entry.getValue().deepCopy());
                        wrapper.set(MARKER_KEY, innerObject);
                        newParent.setAll(wrapper);
                    } else {
                        newParent.set(entry.getKey(), entry.getValue());
                    }
                });

                // Replace parent's content with new ordered content
                parentNode.removeAll();
                parentNode.setAll(newParent);

            } else if (parent instanceof ArrayNode parentArray) {
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
                ObjectNode wrapper = JSON_MAPPER.createObjectNode();
                wrapper.set(MARKER_KEY, targetNode.deepCopy());
                parentArray.set(index, wrapper);

            } else {
                throw new IllegalArgumentException("Parent is neither an object nor an array: " + jsonPath);
            }
        }

        // Convert to YAML
        String yaml = YAML_MAPPER.writeValueAsString(workingCopy);

        // Replace marker with line prefixes
        return markLinesWithPrefix(yaml);
    }

    /**
     * Marks lines inside the error marker with ">" prefix at the left edge and red color.
     * All lines (marked and unmarked) are indented BASE_INDENT spaces from the left.
     */
    private static String markLinesWithPrefix(String yaml) {
        String[] lines = yaml.split("\n");
        StringBuilder result = new StringBuilder();

        result.append("Config:\n");

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
                String firstContentTrimmed = firstContentLine.trim();

                // Calculate the actual indentation for this content
                int actualIndent = markerIndent + (isArrayElement ? 2 : 0);

                // Output the first line with > at the left edge, then BASE_INDENT, then proper indentation
                result.append(RED());
                result.append("> ");
                result.append(" ".repeat(actualIndent));
                result.append(firstContentTrimmed)
                      .append("\n");

                i++; // Move to next line

                // Copy remaining nested content with > prefix at left edge
                while (i < lines.length && getIndentation(lines[i]) > contentIndent) {
                    String nestedLine = lines[i];
                    int nestedIndent = getIndentation(nestedLine);

                    // Calculate the actual indentation for nested content
                    int relativeIndent = nestedIndent - contentIndent;
                    int nestedActualIndent = actualIndent + relativeIndent;

                    result.append(RED());
                    result.append("> ");
                    result.append(" ".repeat(nestedActualIndent))
                          .append(nestedLine.trim())
                          .append("\n");
                    i++;
                }

                result.append(RESET());
                i--; // Back up one since loop will increment
                continue;
            }

            // No marker found, copy line as-is in cyan with BASE_INDENT
            result.append(CYAN())
                  .append("  ") // BASE_INDENT spaces
                  .append(line)
                  .append("\n")
                  .append(RESET());
        }

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

    private static String getParentPath(String jsonPath) {
        // Handle both $.parent.child and $.parent[0] formats
        int lastDot = jsonPath.lastIndexOf('.');
        int lastBracket = jsonPath.lastIndexOf('[');

        if (lastBracket > lastDot) {
            // Last segment is array index like [0]
            return jsonPath.substring(0, lastBracket);
        } else {
            // Last segment is object key like .field
            return jsonPath.substring(0, lastDot);
        }
    }

    private static String getLastSegment(String jsonPath) {
        // Handle both $.parent.child and $.parent[0] formats
        int lastDot = jsonPath.lastIndexOf('.');
        int lastBracket = jsonPath.lastIndexOf('[');

        if (lastBracket > lastDot) {
            // Array index like [0]
            String bracket = jsonPath.substring(lastBracket);
            return bracket.substring(1, bracket.length() - 1); // Extract "0" from "[0]"
        } else {
            // Object key like .field
            return jsonPath.substring(lastDot + 1);
        }
    }
}
