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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;

/**
 * The {@code message_delta} event, which carries the usage of a streamed answer. The usage is null
 * until the event that reports it arrives.
 */
public record MessageDelta(String stopReason,
                           int inputTokens,
                           int outputTokens,
                           int cacheCreationInputTokens,
                           int cacheReadInputTokens,
                           Usage usage) {

    public static MessageDelta from(ObjectNode on) {
        var stopReason = on.path("delta").path("stop_reason").asText(null);

        var u = on.path("usage");
        if (!u.isObject()) {
            return new MessageDelta(stopReason, 0, 0, 0, 0, null);
        }

        var inputTokens = u.path("input_tokens").asInt(0);
        var outputTokens = u.path("output_tokens").asInt(0);
        var cacheCreationInputTokens = u.path("cache_creation_input_tokens").asInt(0);
        var cacheReadInputTokens = u.path("cache_read_input_tokens").asInt(0);

        // Cache tokens are billable according to Claude's pricing model
        var effectiveInputTokens = inputTokens + cacheCreationInputTokens + cacheReadInputTokens;

        return new MessageDelta(stopReason, inputTokens, outputTokens,
                cacheCreationInputTokens, cacheReadInputTokens,
                new Usage(effectiveInputTokens, outputTokens, effectiveInputTokens + outputTokens));
    }
}
