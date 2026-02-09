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

import java.util.*;

public final class NodeValidationUtils {

    public static void ensureMappingStart(JsonNode node) throws ConfigurationParsingException {
        if (!(node.isObject())) throw new ConfigurationParsingException("Expected object");
    }

    public static void ensureSingleKey(ParsingContext<?> ctx, JsonNode node) {
        ensureMappingStart(node);
        if (node.size() != 1) {
            var e = new ConfigurationParsingException("Expected exactly one key but there are %d.".formatted(node.size()));
            e.setParsingContext(ctx);
            throw e;
        }
    }

    public static void ensureTextual(JsonNode node, String message) throws ConfigurationParsingException {
        if (!node.isTextual()) throw new ConfigurationParsingException(message);
    }

    public static void ensureArray(ParsingContext pc, JsonNode node, String message) throws ConfigurationParsingException {
        if (node.isArray())
            return;

        var e = new ConfigurationParsingException(message, null, pc);
        e.setWrong(node);
        throw e;
    }

    public static void ensureArray(ParsingContext pc, JsonNode node) throws ConfigurationParsingException {
        ensureArray(pc, node, "Expected list.");
    }

}
