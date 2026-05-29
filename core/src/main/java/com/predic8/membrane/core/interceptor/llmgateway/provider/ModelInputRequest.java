package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.predic8.membrane.core.http.AbstractBody;

import java.util.List;

public interface ModelInputRequest extends JSONMessage {

    String getModel();

    /**
     * The max number of tokens that the model is allowed to generate as specified by the client.
     * @return The max number of tokens that the model is allowed to generate. -1 if no limit is set.
     */
    long getRequestedMaxOutputTokens();

    void setMaxOutputTokens(int maxOutputTokens);

    long estimateInputTokens();

    List<String> getTools();

    String getSystemPrompt();

    void setSystemPrompts(List<String> prompts);

    void removeSystemPrompt();

    AbstractBody getBody();
}
