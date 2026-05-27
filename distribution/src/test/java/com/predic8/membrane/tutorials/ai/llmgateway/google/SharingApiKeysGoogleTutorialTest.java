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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for
 * {@code distribution/tutorials/ai/llm-gateway/google/20-Sharing-API-Keys.yaml}.
 *
 * <p>The tutorial demonstrates sharing a single upstream API key between multiple users,
 * each identified by their own gateway key and subject to individual token budgets:
 * <ul>
 *   <li><b>alice</b> — key {@code abc123}, budget 500 tokens</li>
 *   <li><b>bob</b>   — key {@code qwertz}, budget 10 000 tokens</li>
 * </ul>
 * Additional gateway limits: {@code maxInputTokens=100}, {@code maxOutputTokens=200},
 * allowed models: {@code gemini-2.5-pro}, {@code gemini-2.5-flash}, {@code gemini-2.5-flash-lite},
 * {@code gemini-2.0-flash}, {@code gemini-2.0-flash-lite}.
 *
 * <p>For Google Gemini the model is part of the URL path
 * ({@code /v1beta/models/<model>:generateContent}), not the request body.
 */
public class SharingApiKeysGoogleTutorialTest extends AbstractGoogleTutorialTest {

    private static final String ALICE = "abc123";
    private static final String BOB   = "qwertz";

    private static final String GEMINI_FLASH_ENDPOINT =
            LOCALHOST_2000 + "/v1beta/models/gemini-2.5-flash:generateContent";

    @Override
    protected String getTutorialYaml() {
        return "20-Sharing-API-Keys.yaml";
    }

    @Test
    void aliceCanSendRequest() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", ALICE)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(GEMINI_FLASH_ENDPOINT)
        .then()
            .statusCode(200)
            .body("candidates[0].content.parts[0].text", equalTo("I am a mock."));
        // @formatter:on
    }

    @Test
    void bobCanSendRequest() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", BOB)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(GEMINI_FLASH_ENDPOINT)
        .then()
            .statusCode(200)
            .body("candidates[0].content.parts[0].text", equalTo("I am a mock."));
        // @formatter:on
    }

    @Test
    void unknownApiKeyIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", "invalid-key")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(GEMINI_FLASH_ENDPOINT)
        .then()
            .statusCode(401)
            .body("error.status", equalTo("UNAUTHENTICATED"))
            .body("error.message", containsString("Invalid API key"));
        // @formatter:on
    }

    /**
     * The gateway is configured with its own upstream {@code apiKey}. When a user request
     * arrives carrying the user-facing key (e.g. alice's {@code abc123}), the gateway must
     * replace it with the configured upstream key before forwarding to the LLM provider.
     * For Google Gemini, the key is carried in the {@code x-goog-api-key} header.
     */
    @Test
    void userApiKeyIsReplacedWithGatewayApiKey() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", ALICE)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(GEMINI_FLASH_ENDPOINT)
        .then()
            .log().ifValidationFails()
            .statusCode(200);
        // @formatter:on

        assertThat(lastRequestApiKey, not(equalTo(ALICE)));
        assertThat(lastRequestApiKey, equalTo(TEST_API_KEY));
    }

    /**
     * For Google Gemini the model is extracted from the URL path. Sending a request to
     * {@code /v1beta/models/gpt-5:generateContent} uses model {@code gpt-5}, which is not
     * in the allowed list, so the gateway rejects it with 400.
     */
    @Test
    void wrongModelIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", ALICE)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1beta/models/gpt-5:generateContent")
        .then()
            .statusCode(400)
            .body("error.status", equalTo("INVALID_ARGUMENT"))
            .body("error.message", containsString("gpt-5"))
            .body("error.message", containsString("not allowed"));
        // @formatter:on
    }

    @Test
    void inputTokenLimitExceededIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", ALICE)
            .body(readFileFromBaseDir("max-input.json"))
        .when()
            .post(GEMINI_FLASH_ENDPOINT)
        .then()
            .statusCode(400)
            .body("error.status", equalTo("INVALID_ARGUMENT"))
            .body("error.message", containsString("exceeds the maximum allowed"))
            .body("error.message", containsString("100"));
        // @formatter:on
    }

    /**
     * Alice has a budget of 500 tokens. Each request with {@code max-output.json} projects
     * 9 (input estimate) + 200 (capped maxOutputTokens) = 209 tokens. The mock returns
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
                .header("x-goog-api-key", ALICE)
                .body(readFileFromBaseDir("max-output.json"))
            .when()
                .post(GEMINI_FLASH_ENDPOINT)
            .then()
                .statusCode(200);
            // @formatter:on
        }

        // Alice's budget is now exhausted
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-goog-api-key", ALICE)
            .body(readFileFromBaseDir("max-output.json"))
        .when()
            .post(GEMINI_FLASH_ENDPOINT)
        .then()
            .statusCode(429)
            .body("error.status", equalTo("RESOURCE_EXHAUSTED"));

        // Bob's budget is independent — he can still send requests
        given()
            .contentType("application/json")
            .header("x-goog-api-key", BOB)
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(GEMINI_FLASH_ENDPOINT)
        .then()
            .statusCode(200)
            .body("candidates[0].content.parts[0].text", equalTo("I am a mock."));
        // @formatter:on
    }
}
