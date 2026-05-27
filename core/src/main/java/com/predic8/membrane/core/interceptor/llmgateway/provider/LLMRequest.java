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

package com.predic8.membrane.core.interceptor.llmgateway.provider;

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

    String getSystemPrompt();

    boolean isChatCompletion();

    void setSystemPrompt(String systemPrompt);

    void removeSystemPrompt();
}

