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
import com.predic8.membrane.core.interceptor.llmgateway.provider.*;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider.endpointPath;
import static com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider.started;
import static com.predic8.membrane.core.interceptor.llmgateway.provider.google.GoogleLLMRequest.X_GOOG_API_KEY;

/**
 * @description (Experimental) Talks to the Google Gemini API, where the model is named in the path rather than in the request body,
 * as in <code>/v1beta/models/gemini-2.5-pro:generateContent</code>. The api key travels in the
 * <code>x-goog-api-key</code> header. See tutorials/llm-gateway/google/10-Basic-LLM-Gateway.yaml.
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

    /**
     * E.g. /v1beta/models/gemini-2.5-pro:generateContent, also with the colon percent-encoded.
     */
    private static final Pattern GENERATE_CONTENT = Pattern.compile("/v1(beta)?/models/[\\w.-]+(:|%3[aA])(generateContent|streamGenerateContent)");

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) throws IOException {
        if (GENERATE_CONTENT.matcher(endpointPath(exchange)).matches()) {
            return new GoogleLLMRequest(exchange);
        }
        // Other endpoints, like batchGenerateContent, only get the key in the header Gemini expects it in.
        return new BaseLLMRequest(exchange) {
            @Override
            protected String apiKeyHeaderName() {
                return X_GOOG_API_KEY;
            }

            @Override
            protected boolean apiKeyIsBearer() {
                return false;
            }
        };
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
