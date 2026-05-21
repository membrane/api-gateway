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

package com.predic8.membrane.tutorials.ai.llmgateway.google;

import com.predic8.membrane.tutorials.ai.llmgateway.AbstractAiTutorialTest;

/**
 * Base class for Google Gemini LLM-Gateway tutorial tests.
 *
 * <p>Overrides the upstream URL and the API-key header so the mock captures
 * the {@code x-goog-api-key} header that Google uses. The mock response is
 * formatted as a Gemini {@code generateContent} reply and reports 100&nbsp;total
 * tokens (50 prompt + 50 candidates) per call.
 */
public abstract class AbstractGoogleTutorialTest extends AbstractAiTutorialTest {

    /** URL prefix used in both Google tutorial YAML files. */
    @Override
    protected String getUpstreamApiUrl() {
        return "https://generativelanguage.googleapis.com";
    }

    @Override
    protected String getTutorialDir() {
        return "ai/llm-gateway/google";
    }

    /** Google authenticates via the {@code x-goog-api-key} header. */
    @Override
    protected String getApiKeyHeader() {
        return "x-goog-api-key";
    }

    /**
     * Minimal Gemini {@code generateContent} reply with 50 prompt + 50 candidates = 100 total
     * tokens. The higher per-request cost keeps the token-budget exhaustion test to three
     * successful requests before alice's 500-token allowance runs out.
     */
    @Override
    protected String mockResponse() {
        return """
                {"candidates":[{"content":{"parts":[{"text":"I am a mock."}],"role":"model"},\
                "finishReason":"STOP"}],\
                "usageMetadata":{"promptTokenCount":50,"candidatesTokenCount":50,"totalTokenCount":100}}""";
    }
}
