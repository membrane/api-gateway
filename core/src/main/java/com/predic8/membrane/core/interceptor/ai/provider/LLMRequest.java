package com.predic8.membrane.core.interceptor.ai.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface LLMRequest {

    String getModel();

    String getApiKey();

    void setApiKey(String apiKey);

    void setMaxOutputTokens(int maxOutputTokens);

    long estimateInputTokens();

    ObjectNode getJson();

    List<String> getTools();

}
