package com.predic8.membrane.core.interceptor.ai.provider.openai;

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
        return tools.valueStream().map(n -> {
            String type;
            if (n.has("type")) {
                type = n.get("type").asText();
                if (!"function".equals(type))
                    return null;
            }
            return n.get("name").asText();
        }).toList();
    }

    @Override
    public long getRequestedMaxOutputTokens() {
        if (json.has("max_output_tokens"))
            return json.get("max_output_tokens").asLong();
        return 0;
    }

    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
            json.put("max_output_tokens", maxOutputTokens);
    }
}
