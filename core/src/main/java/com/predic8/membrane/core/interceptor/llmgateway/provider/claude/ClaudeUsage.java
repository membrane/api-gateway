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

package com.predic8.membrane.core.interceptor.llmgateway.provider.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;

/**
 * Reads a Claude usage node. Anthropic reports the tokens read from and written to the prompt cache
 * separately from the plain input tokens and bills all three, so the input Membrane accounts for is
 * their sum.
 */
final class ClaudeUsage {

    private ClaudeUsage() {
    }

    static int effectiveInputTokens(JsonNode usage) {
        return usage.path("input_tokens").asInt(0)
                + usage.path("cache_creation_input_tokens").asInt(0)
                + usage.path("cache_read_input_tokens").asInt(0);
    }

    static Usage from(JsonNode usage) {
        var inputTokens = effectiveInputTokens(usage);
        var outputTokens = usage.path("output_tokens").asInt(0);
        return new Usage(inputTokens, outputTokens, inputTokens + outputTokens);
    }
}
