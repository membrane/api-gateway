package com.predic8.membrane.core.interceptor.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Header;

@MCElement( name="claude")
public class Claude implements AiProvider{

    public static final String X_API_KEY = "x-api-key";

    @Override
    public void setApiKey(Header header, String apiKey) {
        header.removeFields(X_API_KEY);
        header.add(X_API_KEY, apiKey);
    }

    @Override
    public long estimateInputTokens(JsonNode request) {
        int tokens = 0;

        // System prompt
        tokens += request.path("system").asText().length() / 4;

        // Messages
        for (JsonNode message : request.path("messages")) {
            JsonNode content = message.path("content");
            if (content.isTextual()) {
                tokens += content.asText().length() / 4;
            } else if (content.isArray()) {
                for (JsonNode block : content) {
                    String type = block.path("type").asText();
                    if (type.equals("text")) {
                        tokens += block.path("text").asText().length() / 4;
                    } else if (type.equals("image")) {
                        tokens += 1000;
                    }
                }
            }
        }

        return tokens;
    }

    @Override
    public String getApiKey(Header header) {
        return header.getFirstValue(X_API_KEY);
    }
}
