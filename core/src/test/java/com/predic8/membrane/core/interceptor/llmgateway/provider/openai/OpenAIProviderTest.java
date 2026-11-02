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

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAIProviderTest {

    /**
     * The response is read with the same API the request was read with, so the path is compared the
     * way routing compares it.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/v1/responses, responses",
            "/v1/responses?stream=true, responses",
            "/v1//responses, responses",
            "/v1;x=1/responses, responses",
            "/v1/responses/resp_1, chatCompletions",
            "/v1/chat/completions, chatCompletions",
            "/v1/models, chatCompletions",
    })
    void responseHandlerFollowsRoutingPath(String path, String expected) throws Exception {
        var exchange = post("http://localhost" + path).json("{}").buildExchange();
        exchange.setResponse(Response.ok().json("{}").build());

        assertEquals(expected, api(new OpenAIProvider().getLLMResponse(exchange, response -> {})));
    }

    private static String api(LLMResponse response) {
        return switch (response) {
            case OpenAiLLMResponsesResponse ignored -> "responses";
            case ChatCompletionsResponse ignored -> "chatCompletions";
            default -> throw new IllegalArgumentException(response.getClass().getName());
        };
    }
}
