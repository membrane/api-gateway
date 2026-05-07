package com.predic8.membrane.core.interceptor.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.core.http.Header;

public interface AiProvider {

    void setApiKey(Header header, String apiKey);

    long estimateInputTokens(JsonNode json);

    String getApiKey(Header header);
}
