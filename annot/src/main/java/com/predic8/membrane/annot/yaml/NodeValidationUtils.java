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

import com.fasterxml.jackson.databind.JsonNode;

public final class NodeValidationUtils {

    public static void ensureMappingStart(JsonNode node) throws YamlParsingException {
        if (!(node.isObject())) throw new YamlParsingException("Expected object", node);
    }

    public static void ensureSingleKey(JsonNode node) {
        ensureMappingStart(node);
        if (node.size() != 1) throw new YamlParsingException("Expected exactly one key.", node);
    }

    public static void ensureTextual(JsonNode node, String message) throws YamlParsingException {
        if (!node.isTextual()) throw new YamlParsingException(message, node);
    }

    public static void ensureArray(JsonNode node, String message) throws YamlParsingException {
        if (!node.isArray()) throw new YamlParsingException(message, node);
    }

    public static void ensureArray(JsonNode node) throws YamlParsingException {
        ensureArray(node, "Expected list.");
    }

}
