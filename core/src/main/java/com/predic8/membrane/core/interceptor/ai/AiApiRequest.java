package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface AiApiRequest {

    String getModel();

    String getApiKey();

    void setApiKey(String apiKey);

    void setMaxOutputTokens(int maxOutputTokens);

    long estimateInputTokens();

    ObjectNode getJson();

}
