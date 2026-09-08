/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Estimates how many tokens a request costs by counting the characters it carries. The gateway needs
 * a number before it forwards the request, when the provider's count is not available yet, so this is
 * deliberately an approximation.
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    /**
     * The characters of the text a node holds. Text is either the node itself, the {@code text} of a
     * content block, or whatever the {@code content} and {@code parts} of a block contain.
     */
    public static long countText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull())
            return 0;

        if (node.isTextual())
            return node.asText().length();

        if (node.isArray()) {
            long chars = 0;
            for (JsonNode child : node)
                chars += countText(child);
            return chars;
        }

        if (node.isObject()) {
            long chars = 0;
            var text = node.get("text");
            if (text != null && text.isTextual())
                chars += text.asText().length();
            chars += countText(node.get("content"));
            chars += countText(node.get("parts"));
            return chars;
        }

        return 0;
    }

    /**
     * Tool and function definitions are sent verbatim, so their whole JSON counts, not just their text.
     */
    public static long countJsonSize(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull())
            return 0;
        return node.toString().length();
    }

    /**
     * Roughly four characters per token, plus a margin for the JSON structure, the roles and the
     * variance of the provider's tokenizer. Never less than one token.
     */
    public static long charsToTokens(long chars) {
        return Math.max(1, Math.round(chars / 4.0 * 1.15));
    }
}
