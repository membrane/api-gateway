package com.predic8.membrane.core.interceptor.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Header;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;

@MCElement( name="openai")
public class OpenAI implements AiProvider{

    public static final String BEARER_PREFIX = "Bearer";

    @Override
    public void setApiKey(Header header, String apiKey) {
        header.removeFields(AUTHORIZATION);
        header.add(AUTHORIZATION, "Bearer " + apiKey);
    }

    @Override
    public long estimateInputTokens(JsonNode json) {
        var input = json.path("input").asText();
        if (input == null)
            return 0;
        return (long) Math.ceil(input.length() / 4.0);
    }

    @Override
    public String getApiKey(Header header) {
        var ah = header.getAuthorization();
        if (ah == null) {
            return null;
        }

        int index = ah.indexOf(BEARER_PREFIX);
        if (index < 0) {
            return null;
        }

        var token = ah.substring(index + BEARER_PREFIX.length()).trim();

        return token.isEmpty() ? null : token;
    }
}
