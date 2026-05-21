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

package com.predic8.membrane.tutorials.ai.llmgateway.openai;

import com.predic8.membrane.tutorials.ai.llmgateway.AbstractAiTutorialTest;

/**
 * Base class for OpenAI LLM-Gateway tutorial tests.
 *
 * <p>Overrides the upstream URL and the API-key header so the mock captures
 * the {@code Authorization} header that OpenAI uses instead of {@code x-api-key}.
 * The mock response is formatted as an OpenAI Responses-API reply and reports
 * 100&nbsp;total tokens (50 input + 50 output) per call.
 */
public abstract class AbstractOpenAiTutorialTest extends AbstractAiTutorialTest {

    @Override
    protected String getTutorialDir() {
        return "ai/llm-gateway/openai";
    }

    @Override
    protected String getUpstreamApiUrl() {
        return "https://api.openai.com";
    }

    /**
     * OpenAI authenticates via {@code Authorization: Bearer <token>}.
     * The full header value (including the "Bearer " prefix) is captured.
     */
    @Override
    protected String getApiKeyHeader() {
        return "authorization";
    }

    /**
     * Minimal OpenAI Responses-API reply with 50 input + 50 output = 100 total tokens.
     * The higher per-request cost (vs. the default Claude mock) keeps the token-budget
     * exhaustion test to three successful requests before alice's 500-token allowance runs out.
     */
    @Override
    protected String mockResponse() {
        return """
                {"id":"resp_mock","object":"response","model":"gpt-5-nano",\
                "output":[{"type":"message","role":"assistant",\
                "content":[{"type":"output_text","text":"I am a mock."}]}],\
                "usage":{"input_tokens":50,"output_tokens":50,"total_tokens":100}}""";
    }
}
