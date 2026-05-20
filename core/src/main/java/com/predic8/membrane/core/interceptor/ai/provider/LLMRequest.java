package com.predic8.membrane.core.interceptor.ai.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface LLMRequest {

    String getModel();

    String getApiKey();

    void setApiKey(String apiKey);

    /**
     * The max number of tokens that the model is allowed to generate as specified by the client.
     * @return The max number of tokens that the model is allowed to generate. -1 if no limit is set.
     */
    long getRequestedMaxOutputTokens();

    void setMaxOutputTokens(int maxOutputTokens);

    long estimateInputTokens();

    ObjectNode getJson();

    List<String> getTools();

}
