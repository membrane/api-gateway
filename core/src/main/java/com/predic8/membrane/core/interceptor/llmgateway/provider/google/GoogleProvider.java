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

package com.predic8.membrane.core.interceptor.llmgateway.provider.google;

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
 * @description (Experimental) Talks to the Google Gemini API, where the model is named in the path rather than in the request body,
 * as in <code>/v1beta/models/gemini-2.5-pro:generateContent</code>. The api key travels in the
 * <code>x-goog-api-key</code> header. See tutorials/ai/llm-gateway/google/10-Basic-LLM-Gateway.yaml.
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - llmGateway:
 *         google: {}
 *         policies:
 *           maxOutputTokens: 200
 *   target:
 *     url: https://generativelanguage.googleapis.com
 * </code></pre>
 */
@MCElement( name="google",id = "google-ai-provider")
public class GoogleProvider implements LLMProvider {

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) throws IOException {
        return new GoogleLLMRequest(exchange);
    }

    @Override
    public LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor) {
        return started(new GoogleLLMResponse(request, postProcessor));
    }

    @Override
    public LLMErrorCreator getErrorCreator() {
        return new GoogleErrorCreator();
    }
}
