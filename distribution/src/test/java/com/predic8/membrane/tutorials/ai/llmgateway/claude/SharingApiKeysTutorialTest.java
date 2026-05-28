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

package com.predic8.membrane.tutorials.ai.llmgateway.claude;

import com.predic8.membrane.tutorials.ai.llmgateway.AbstractAiTutorialTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for
 * {@code distribution/tutorials/ai/llm-gateway/claude/20-Sharing-API-Keys.yaml}.
 *
 * <p>The tutorial demonstrates sharing a single upstream API key between multiple users,
 * each identified by their own gateway key and subject to individual token budgets:
 * <ul>
 *   <li><b>alice</b> — key {@code abc123}, budget 250 tokens</li>
 *   <li><b>bob</b>   — key {@code qwertz}, budget 10 000 tokens</li>
 * </ul>
 * Additional gateway limits: {@code maxInputTokens=100}, {@code maxOutputTokens=200},
 * allowed models: {@code claude-sonnet-4-0}, {@code claude-opus-4-0}, {@code claude-haiku-3-5}.
 */
public class SharingApiKeysTutorialTest extends AbstractAiTutorialTest {

    private static final String ALICE = "abc123";
    private static final String BOB   = "qwertz";

    @Override
    protected String getTutorialDir() {
        return "ai/llm-gateway/claude";
    }

    @Override
    protected String getTutorialYaml() {
        return "20-Sharing-API-Keys.yaml";
    }

    @Test
    void aliceCanSendRequest() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", ALICE)
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(200)
            .body("type", equalTo("message"));
        // @formatter:on
    }

    @Test
    void bobCanSendRequest() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", BOB)
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(200)
            .body("type", equalTo("message"));
        // @formatter:on
    }

    @Test
    void unknownApiKeyIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", "invalid-key")
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(401)
            .body("type", equalTo("error"))
            .body("error.type", equalTo("authentication_error"));
        // @formatter:on
    }

    /**
     * The gateway is configured with its own upstream {@code apiKey}. When a user request
     * arrives carrying the user-facing key (e.g. alice's {@code abc123}), the gateway must
     * replace it with the configured upstream key before forwarding to the LLM provider.
     */
    @Test
    void userApiKeyIsReplacedWithGatewayApiKey() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", ALICE)
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(200);
        // @formatter:on

        assertThat(lastRequestApiKey, not(equalTo(ALICE)));
        assertThat(lastRequestApiKey, equalTo(TEST_API_KEY));
    }

    @Test
    void wrongModelIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", ALICE)
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("wrong-model.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(400)
            .body("type", equalTo("error"))
            .body("error.type", equalTo("invalid_request_error"))
            .body("error.message", containsString("gpt-5"))
            .body("error.message", containsString("not allowed"));
        // @formatter:on
    }

    @Test
    void inputTokenLimitExceededIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", ALICE)
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("max-input.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(400)
            .body("type", equalTo("error"))
            .body("error.type", equalTo("invalid_request_error"))
            .body("error.message", containsString("prompt is too long"))
            .body("error.message", containsString("100 maximum"));
        // @formatter:on
    }

    /**
     * Alice has a budget of 250 tokens. Each request with {@code max-output.json} projects
     * 7 (input estimate) + 200 (capped max_tokens) = 207 tokens. The mock returns 15 tokens
     * of actual usage per call, so the running total grows by 15 after each response.
     *
     * <p>Budget accounting per request:
     * <pre>
     *   1st: 250 - 0   - 207 =  43  → forwarded; used becomes 15
     *   2nd: 250 - 15  - 207 =  28  → forwarded; used becomes 30
     *   3rd: 250 - 30  - 207 =  13  → forwarded; used becomes 45
     *   4th: 250 - 45  - 207 =  -2  → rejected with 429
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
                .header("x-api-key", ALICE)
                .header("anthropic-version", "2023-06-01")
                .body(readFileFromBaseDir("max-output.json"))
            .when()
                .post(LOCALHOST_2000 + "/v1/messages")
            .then()
                .statusCode(200);
            // @formatter:on
        }

        // Alice's budget is now exhausted
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", ALICE)
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("max-output.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(429)
            .body("type", equalTo("error"))
            .body("error.type", equalTo("rate_limit_error"));

        // Bob's budget is independent — he can still send requests
        given()
            .contentType("application/json")
            .header("x-api-key", BOB)
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(200)
            .body("type", equalTo("message"));
        // @formatter:on
    }
}
