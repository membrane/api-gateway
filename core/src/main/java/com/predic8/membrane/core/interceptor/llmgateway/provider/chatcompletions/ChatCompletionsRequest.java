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

package com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.AbstractOpenAiLLMRequest;

import java.util.List;

import static java.util.Collections.emptyList;

public class ChatCompletionsRequest extends AbstractOpenAiLLMRequest {

    public ChatCompletionsRequest(Exchange exchange) {
        super(exchange);

        if (json == null) {
            return;
        }

        // Make sure that when streaming is enabled, the usage is included in the response.
        if (json.path("stream").asBoolean(false)) {
            var streamOptions = json.withObject("/stream_options");
            streamOptions.put("include_usage", true);
        }
    }

    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
        json.put("max_tokens", maxOutputTokens);
    }

    public List<String> getTools() {
        var tools = getToolsNode();
        if (tools == null)
            return emptyList();
        return tools.valueStream()
                .filter(n -> "function".equals(n.path("type").asText("")))
                .map(n -> n.path("function").path("name").asText(""))
                .filter(name -> !name.isEmpty())
                .toList();
    }

    @Override
    public long getRequestedMaxOutputTokens() {
        return json.path("max_completion_tokens").asLong(0);
    }

}
