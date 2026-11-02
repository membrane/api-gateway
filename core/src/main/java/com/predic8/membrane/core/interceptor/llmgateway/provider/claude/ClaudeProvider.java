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

package com.predic8.membrane.core.interceptor.llmgateway.provider.claude;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.*;

import java.io.IOException;
import java.util.function.Consumer;

import static com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider.endpointPath;
import static com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider.started;
import static com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeLLMRequest.X_API_KEY;

/**
 * @description (Experimental) Talks to the Anthropic Claude Messages API under <code>/v1/messages</code>. The api key travels in the
 * <code>x-api-key</code> header, and the system prompt in the top level <code>system</code> field. See
 * tutorials/llm-gateway/claude/10-Basic-LLM-Gateway.yaml.
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - llmGateway:
 *         claude: {}
 *         policies:
 *           maxOutputTokens: 200
 *   target:
 *     url: https://api.anthropic.com
 * </code></pre>
 */
@MCElement( name="claude")
public class ClaudeProvider implements LLMProvider {

    private static final String MESSAGES = "/v1/messages";

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) throws IOException {
        if (MESSAGES.equals(endpointPath(exchange))) {
            return new ClaudeLLMRequest(exchange);
        }
        // Other endpoints, like the batches, only get the key in the header Claude expects it in.
        return new BaseLLMRequest(exchange) {
            @Override
            protected String apiKeyHeaderName() {
                return X_API_KEY;
            }

            @Override
            protected boolean apiKeyIsBearer() {
                return false;
            }
        };
    }

    @Override
    public LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor) {
        return started(new ClaudeLLMResponse(request, postProcessor));
    }

    @Override
    public LLMErrorCreator getErrorCreator() {
        return new ClaudeErrorCreator();
    }
}
