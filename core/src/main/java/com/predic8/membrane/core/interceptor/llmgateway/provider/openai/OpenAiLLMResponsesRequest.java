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

package com.predic8.membrane.core.interceptor.llmgateway.provider.openai;

import com.predic8.membrane.core.exchange.Exchange;

import java.util.List;

import static java.util.Collections.emptyList;

public class OpenAiLLMResponsesRequest extends AbstractOpenAiLLMRequest {

    public OpenAiLLMResponsesRequest(Exchange exchange) {
        super(exchange);
    }

    public List<String> getTools() {
        var tools = getToolsNode();
        if (tools == null)
            return emptyList();
        return tools.valueStream()
                .filter(n -> "function".equals(n.path("type").asText("")))
                .map(n -> n.path("name").asText(""))
                .filter(name -> !name.isEmpty())
                .toList();
    }

    @Override
    public long getRequestedMaxOutputTokens() {
        if (json.has("max_output_tokens"))
            return json.get("max_output_tokens").asLong();
        return -1;
    }

    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
        json.put("max_output_tokens", maxOutputTokens);
    }
}
