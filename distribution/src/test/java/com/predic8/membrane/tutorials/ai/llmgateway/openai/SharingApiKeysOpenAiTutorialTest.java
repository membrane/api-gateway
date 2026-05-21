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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for
 * {@code distribution/tutorials/ai/llm-gateway/openai/20-Sharing-API-Keys.yaml}.
 *
 * <p>The tutorial demonstrates sharing a single upstream API key between multiple users,
 * each identified by their own gateway key and subject to individual token budgets:
 * <ul>
 *   <li><b>alice</b> — key {@code abc123}, budget 500 tokens</li>
 *   <li><b>bob</b>   — key {@code qwertz}, budget 10 000 tokens</li>
 * </ul>
 * Additional gateway limits: {@code maxInputTokens=100}, {@code maxOutputTokens=200},
 * allowed models: {@code gpt-5.4}, {@code gpt-5-nano}, {@code gpt-5-mini}.
 */
public class SharingApiKeysOpenAiTutorialTest extends AbstractOpenAiTutorialTest {

    private static final String ALICE = "abc123";
    private static final String BOB   = "qwertz";

    @Override
    protected String getTutorialYaml() {
        return "20-Sharing-API-Keys.yaml";
    }

    @Test
    void aliceCanSendRequest() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer " + ALICE)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(200)
            .body("object", equalTo("response"));
        // @formatter:on
    }

    @Test
    void bobCanSendRequest() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer " + BOB)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(200)
            .body("object", equalTo("response"));
        // @formatter:on
    }

    @Test
    void unknownApiKeyIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer invalid-key")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("invalid_authentication"));
        // @formatter:on
    }

    /**
     * The gateway is configured with its own upstream {@code apiKey}. When a user request
     * arrives carrying the user-facing key (e.g. alice's {@code abc123}), the gateway must
     * replace it with the configured upstream key before forwarding to the LLM provider.
     * For OpenAI, the key is carried in the {@code Authorization: Bearer <token>} header.
     */
    @Test
    void userApiKeyIsReplacedWithGatewayApiKey() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer " + ALICE)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(200);
        // @formatter:on

        assertThat(lastRequestApiKey, not(equalTo("Bearer " + ALICE)));
        assertThat(lastRequestApiKey, equalTo("Bearer <<Replace with your API_KEY>>"));
    }

    @Test
    void wrongModelIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer " + ALICE)
            .body(readFileFromBaseDir("wrong-model.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(400)
            .body("error.type", equalTo("invalid_request_error"))
            .body("error.code", equalTo("model_not_allowed"))
            .body("error.message", containsString("gpt-4"))
            .body("error.message", containsString("not allowed"));
        // @formatter:on
    }

    @Test
    void inputTokenLimitExceededIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer " + ALICE)
            .body(readFileFromBaseDir("max-input.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(400)
            .body("error.type", equalTo("invalid_request_error"))
            .body("error.code", equalTo("context_length_exceeded"))
            .body("error.message", containsString("maximum context length"))
            .body("error.message", containsString("100"));
        // @formatter:on
    }

    /**
     * Alice has a budget of 500 tokens. Each request with {@code max-output.json} projects
     * 9 (input estimate) + 200 (capped max_output_tokens) = 209 tokens. The mock returns
     * 100 tokens of actual usage per call, so the running total grows by 100 after each response.
     *
     * <p>Budget accounting per request:
     * <pre>
     *   1st: 500 - 0   - 209 = 291  → forwarded; used becomes 100
     *   2nd: 500 - 100 - 209 = 191  → forwarded; used becomes 200
     *   3rd: 500 - 200 - 209 =  91  → forwarded; used becomes 300
     *   4th: 500 - 300 - 209 =  -9  → rejected with 429
     * </pre>
     *
     * Bob's separate budget of 10 000 tokens is unaffected, so he can still send requests
     * after alice is blocked.
     */
    @Test
    void alicesTokenBudgetIsExhaustedWhileBobIsUnaffected() throws IOException {
        for (int i = 0; i < 3; i++) {
            // @formatter:off
            given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + ALICE)
                .body(readFileFromBaseDir("max-output.json"))
            .when()
                .post(LOCALHOST_2000 + "/v1/responses")
            .then()
                .statusCode(200);
            // @formatter:on
        }

        // Alice's budget is now exhausted
        // @formatter:off
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer " + ALICE)
            .body(readFileFromBaseDir("max-output.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(429)
            .body("error.type", equalTo("rate_limit_error"))
            .body("error.code", equalTo("token_limit_exceeded"));

        // Bob's budget is independent — he can still send requests
        given()
            .contentType("application/json")
            .header("Authorization", "Bearer " + BOB)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/responses")
        .then()
            .statusCode(200)
            .body("object", equalTo("response"));
        // @formatter:on
    }
}
