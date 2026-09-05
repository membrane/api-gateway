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

package com.predic8.membrane.core.interceptor.llmgateway.provider.openai;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.BaseLLMRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsResponse;

import java.io.IOException;
import java.util.function.Consumer;

import static com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider.started;

/**
 * @description Talks to the OpenAI API. Both the Responses API under <code>/v1/responses</code> and the Chat Completions API under
 * <code>/v1/chat/completions</code> are supported, and the endpoint the client calls decides which one is used. The
 * api key travels as a Bearer token in the <code>Authorization</code> header. See
 * tutorials/ai/llm-gateway/openai/10-Basic-LLM-Gateway.yaml.
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - llmGateway:
 *         openai: {}
 *         policies:
 *           maxOutputTokens: 200
 *   target:
 *     url: https://api.openai.com
 * </code></pre>
 */
@MCElement( name="openai")
public class OpenAIProvider implements LLMProvider {

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) throws IOException {
        var uri = exchange.getRequest().getUri();
        if (uri.startsWith("/v1/chat/completions")) {
            return new OpenAIChatCompletionsRequest(exchange);
        }
        if (uri.startsWith("/v1/responses")) {
            return new OpenAiLLMResponsesRequest(exchange);
        }
        return new BaseLLMRequest(exchange);
    }

    @Override
    public LLMResponse getLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        var uri = exchange.getRequest().getUri();
        if (uri.startsWith("/v1/responses")) {
            return started(new OpenAiLLMResponsesResponse(exchange, postProcessor));
        }
        return started(new ChatCompletionsResponse(exchange, postProcessor));
    }

    @Override
    public LLMErrorCreator getErrorCreator() {
        return new ChatCompletionsErrorCreator();
    }

}
