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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.path.json.JsonPath.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integration test for
 * {@code distribution/tutorials/ai/llm-gateway/openai/10-Basic-LLM-Gateway.yaml}.
 *
 * <p>The tutorial configures an OpenAI LLM gateway with:
 * <ul>
 *   <li>{@code maxInputTokens: 100} — requests whose estimated input exceeds 100 tokens are rejected</li>
 *   <li>{@code maxOutputTokens: 200} — {@code max_output_tokens} in the forwarded request is capped to 200</li>
 * </ul>
 *
 * <p>The upstream OpenAI API is replaced by a local mock server so no real API key is needed.
 */
public class BasicOpenAiLLMGatewayTutorialTest extends AbstractOpenAiTutorialTest {

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
            .header("Authorization", "Bearer test-key")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(200)
            .body("object", equalTo("response"));
        // @formatter:on
    }

    /**
     * A request whose message content exceeds {@code maxInputTokens} (100) is rejected by the
     * gateway before reaching the upstream. The response uses the OpenAI error format.
     */
    @Test
    void inputTokenLimitExceededIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer test-key")
            .body(readFileFromBaseDir("max-input.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(400)
            .body("error.type", equalTo("invalid_request_error"))
            .body("error.code", equalTo("context_length_exceeded"))
            .body("error.message", containsString("100"));
        // @formatter:on
    }

    /**
     * When the request asks for more output tokens than {@code maxOutputTokens} (200) allows,
     * the gateway rewrites {@code max_output_tokens} to 200 before forwarding to the upstream.
     * The mock captures the forwarded body so we can verify the value was actually capped.
     */
    @Test
    void outputTokensAreCappedBeforeForwarding() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer test-key")
            .body(readFileFromBaseDir("max-output.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(200);
        // @formatter:on

        assertThat(from(lastRequestBody).getInt("max_output_tokens"), equalTo(200));
    }
}
