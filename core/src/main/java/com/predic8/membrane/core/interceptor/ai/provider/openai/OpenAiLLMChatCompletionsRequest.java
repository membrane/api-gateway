package com.predic8.membrane.core.interceptor.ai.provider.openai;

import com.predic8.membrane.core.exchange.Exchange;

import java.util.List;

import static java.util.Collections.emptyList;

public class OpenAiLLMChatCompletionsRequest extends AbstractOpenAiLLMRequest {

    public OpenAiLLMChatCompletionsRequest(Exchange exchange) {
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
        json.put("max_completion_tokens", maxOutputTokens);
    }

    public List<String> getTools() {
        var tools = getToolsNode();
        if (tools == null)
            return emptyList();
        return tools.valueStream().map(n -> {
            String type;
            if (n.has("type")) {
                type = n.get("type").asText();
                if (!"function".equals(type))
                    return null;
            }

            return n.get("function").get("name").asText();
        }).toList();
    }

    @Override
    public long getRequestedMaxOutputTokens() {
        return json.path("max_tokens").asLong(0);
    }

}
