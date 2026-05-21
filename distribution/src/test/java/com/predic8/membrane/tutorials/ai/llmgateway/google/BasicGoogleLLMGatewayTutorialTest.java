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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.path.json.JsonPath.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integration test for
 * {@code distribution/tutorials/ai/llm-gateway/google/10-Basic-LLM-Gateway.yaml}.
 *
 * <p>The tutorial configures a Google Gemini LLM gateway with:
 * <ul>
 *   <li>{@code maxInputTokens: 100} — requests whose estimated input exceeds 100 tokens are rejected</li>
 *   <li>{@code maxOutputTokens: 200} — {@code generationConfig.maxOutputTokens} in the forwarded
 *       request is capped to 200</li>
 * </ul>
 *
 * <p>The upstream Google Gemini API is replaced by a local mock server so no real API key is needed.
 */
public class BasicGoogleLLMGatewayTutorialTest extends AbstractGoogleTutorialTest {

    private static final String GEMINI_ENDPOINT =
            LOCALHOST_2000 + "/v1beta/models/gemini-2.5-flash:generateContent";

    @Override
    protected String getTutorialYaml() {
        return "10-Basic-LLM-Gateway.yaml";
    }

    /**
     * A request within the token limits is forwarded to the upstream and its response is returned.
     */
    @Test
    void simpleRequestIsForwarded() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", "test-key")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(GEMINI_ENDPOINT)
        .then()
            .statusCode(200)
            .body("candidates[0].content.parts[0].text", equalTo("I am a mock."));
        // @formatter:on
    }

    /**
     * A request whose message content exceeds {@code maxInputTokens} (100) is rejected by the
     * gateway before reaching the upstream. The response uses the Google error format.
     */
    @Test
    void inputTokenLimitExceededIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", "test-key")
            .body(readFileFromBaseDir("max-input.json"))
        .when()
            .post(GEMINI_ENDPOINT)
        .then()
            .statusCode(400)
            .body("error.status", equalTo("INVALID_ARGUMENT"))
            .body("error.message", containsString("exceeds the maximum allowed"))
            .body("error.message", containsString("100"));
        // @formatter:on
    }

    /**
     * When the request asks for more output tokens than {@code maxOutputTokens} (200) allows,
     * the gateway rewrites {@code generationConfig.maxOutputTokens} to 200 before forwarding.
     * The mock captures the forwarded body so we can verify the value was actually capped.
     */
    @Test
    void outputTokensAreCappedBeforeForwarding() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", "test-key")
            .body(readFileFromBaseDir("max-output.json"))
        .when()
            .post(GEMINI_ENDPOINT)
        .then()
            .statusCode(200);
        // @formatter:on

        assertThat(from(lastRequestBody).getInt("generationConfig.maxOutputTokens"), equalTo(200));
    }
}
