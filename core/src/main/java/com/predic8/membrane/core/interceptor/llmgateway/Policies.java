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

package com.predic8.membrane.core.interceptor.llmgateway;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

import java.util.List;

/**
 * LLM Gateway policies for token usage and model restrictions.
 */
@MCElement(name = "policies", topLevel = false, id="llm-gateway-policies")
public class Policies {

    private List<String> models;
    private int maxOutputTokens;
    private int maxInputTokens;

    public List<String> getModels() {
        return models;
    }

    /**
     * @param models List of models that can be used by the gateway.
     * @desciption Restricts the models that can be used by the gateway.
     * @default null (no restriction)
     */
    @MCAttribute
    public void setModels(List<String> models) {
        this.models = models;
    }


    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * @param maxOutputTokens Maximum number of tokens the LLM should use to generate a response.
     * @description Maximum number of tokens the LLM should use to generate a response. This is just a hint that the gateway
     * sends to the LLM provider. The provider may use a different limit.
     * @default 0 (unlimited)
     */
    @MCAttribute
    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    /**
     * @param maxInputTokens Maximum number of tokens that a request can use.
     * @description Restricts token usage for the input. The size of the input is estimated by gateway based on the request size.
     * Actual token usage may be deviate from this value.
     */
    @MCAttribute
    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

}
