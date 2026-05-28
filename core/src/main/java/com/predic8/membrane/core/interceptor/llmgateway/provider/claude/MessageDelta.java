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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;

public class MessageDelta {

    private String stopReason;
    private int inputTokens;
    private int outputTokens;
    private int cacheCreationInputTokens;
    private int cacheReadInputTokens;

    private Usage usage;

    public static MessageDelta from(ObjectNode on) {
        var md = new MessageDelta();

        JsonNode delta = on.path("delta");
        md.stopReason = delta.path("stop_reason").asText(null);

        JsonNode u = on.path("usage");
        if (u.isObject()) {
            md.inputTokens = u.path("input_tokens").asInt(0);
            md.outputTokens = u.path("output_tokens").asInt(0);
            md.cacheCreationInputTokens = u.path("cache_creation_input_tokens").asInt(0);
            md.cacheReadInputTokens = u.path("cache_read_input_tokens").asInt(0);

            // Cache tokens (cache_creation_input_tokens and cache_read_input_tokens) are billable according to Claude's pricing model
            int effectiveInputTokens = md.inputTokens + md.cacheCreationInputTokens + md.cacheReadInputTokens;
            md.usage = new Usage(effectiveInputTokens,md.outputTokens, effectiveInputTokens + md.outputTokens);

        }

        return md;
    }

    public String getStopReason() {
        return stopReason;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getCacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    public int getCacheReadInputTokens() {
        return cacheReadInputTokens;
    }

    public Usage getUsage() {
        return usage;
    }

    @Override
    public String toString() {
        return "MessageDelta{" +
                "stopReason='" + stopReason + '\'' +
                ", inputTokens=" + inputTokens +
                ", outputTokens=" + outputTokens +
                ", cacheCreationInputTokens=" + cacheCreationInputTokens +
                ", cacheReadInputTokens=" + cacheReadInputTokens +
                '}';
    }
}