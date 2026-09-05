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

import java.io.IOException;
import java.util.function.Consumer;

import static com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider.started;

/**
 * @description Talks to any service that serves the OpenAI Chat Completions API, so that a gateway can front a provider
 * Membrane has no element of its own for. The api key travels as a Bearer token in the <code>Authorization</code>
 * header, and the target url decides which service is called. Known to work with Azure OpenAI, the OpenAI compatible
 * endpoint of Google Gemini, TogetherAI, Fireworks AI, DeepSeek AI, OpenRouter, Mistral AI, DeepInfra, SiliconFlow,
 * NVIDIA NIM, ML Studio, vLLM and Ollama.
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - llmGateway:
 *         chatCompletions: {}
 *         policies:
 *           maxOutputTokens: 200
 *   target:
 *     url: http://localhost:11434
 * </code></pre>
 */
@MCElement(name = "chatCompletions")
public class ChatCompletionsProvider implements LLMProvider {
    @Override
    public LLMRequest getLLMRequest(Exchange request) throws IOException {
        return new ChatCompletionsRequest(request);
    }

    @Override
    public LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor) {
        return started(new ChatCompletionsResponse(request, postProcessor));
    }

    @Override
    public LLMErrorCreator getErrorCreator() {
        return new ChatCompletionsErrorCreator();
    }
}
