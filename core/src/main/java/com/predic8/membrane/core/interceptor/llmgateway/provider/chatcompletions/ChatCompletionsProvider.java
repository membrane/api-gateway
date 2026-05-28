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

package com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;

import java.util.function.Consumer;

/**
 * @description
 * OpenAI Chat Completions API compatible provider.
 * Can be used for the following providers:
 * <ul>
 *  <li>Azure OpenAI</li>
 *   <li>Google Gemini (OpenAI compatible endpoint)</li>
 *   <li>TogetherAI</li>
 *   <li>Fireworks AI</li>
 *   <li>DeepSeek AI</li>
 *   <li>OpenRouter</li>
 *   <li>Mistral AI</li>
 *   <li>DeepInfra</li>
 *   <li>SiliconFlow</li>
 *   <li>NVIDIA NIM</li>
 *   <li>ML Studio</li>
 *   <li>vLLM</li>
 *   <li>Ollama</li>
 * </ul>
 */
@MCElement(name = "chatCompletions")
public class ChatCompletionsProvider implements LLMProvider {
    @Override
    public LLMRequest getLLMRequest(Exchange request) {
        return new ChatCompletionsRequest(request);
    }

    @Override
    public LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor) {
        return new ChatCompletionsResponse(request, postProcessor);
    }

    @Override
    public LLMErrorCreator getErrorCreator() {
        return new ChatCompletionsErrorCreator();
    }
}
