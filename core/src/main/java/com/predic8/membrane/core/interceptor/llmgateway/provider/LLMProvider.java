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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;

import java.io.IOException;
import java.util.function.Consumer;

import static com.predic8.membrane.core.openapi.util.UriUtil.trimQueryString;

public interface LLMProvider {

    /**
     * Hands a freshly built response to its body. Reading is started outside the constructor, so
     * every provider has to do this before returning the response.
     */
    static <T extends AbstractLLMResponse> T started(T response) {
        response.start();
        return response;
    }

    /**
     * The path of the request without path parameters and query, to decide which endpoint is called.
     * Dot-segments are already resolved and duplicate slashes merged, see {@link Request#getRoutingPath()}.
     */
    static String endpointPath(Exchange exchange) {
        return trimQueryString(exchange.getRequest().getRoutingPath());
    }

    /**
     * Returns a {@link ModelInputRequest} only for the endpoints the provider can read the model input
     * of. The gateway relies on this to reject other endpoints when restrictions are configured.
     */
    LLMRequest getLLMRequest(Exchange request) throws IOException;
    LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor);
    LLMErrorCreator getErrorCreator();

}
