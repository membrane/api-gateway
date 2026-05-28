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
import static io.restassured.path.json.JsonPath.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integration test for {@code distribution/tutorials/ai/llm-gateway/claude/10-Basic-LLM-Gateway.yaml}.
 *
 * <p>The tutorial configures a Claude LLM gateway with:
 * <ul>
 *   <li>{@code maxInputTokens: 100} — requests whose estimated input exceeds 100 tokens are rejected</li>
 *   <li>{@code maxOutputTokens: 200} — {@code max_tokens} in the forwarded request is capped to 200</li>
 * </ul>
 *
 * <p>The upstream Anthropic API is replaced by a local mock server so no real API key is needed.
 */
public class BasicClaudeLLMGatewayTutorialTest extends AbstractAiTutorialTest {

    @Override
    protected String getTutorialDir() {
        return "ai/llm-gateway/claude";
    }

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
            .header("x-api-key", "test-key")
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("simple.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(200)
            .body("type", equalTo("message"))
            .body("content[0].type", equalTo("text"));
        // @formatter:on
    }

    /**
     * A request whose message content exceeds {@code maxInputTokens} (100) is rejected by the
     * gateway before reaching the upstream. The response uses the Claude error format.
     */
    @Test
    void inputTokenLimitExceededIsRejected() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", "test-key")
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("max-input.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(400)
            .body("type", equalTo("error"))
            .body("error.type", equalTo("invalid_request_error"))
            .body("error.message", containsString("tokens"));
        // @formatter:on
    }

    /**
     * When the request asks for more output tokens than {@code maxOutputTokens} (200) allows,
     * the gateway rewrites {@code max_tokens} to 200 before forwarding to the upstream.
     * The mock captures the forwarded body so we can verify the value was actually capped.
     */
    @Test
    void outputTokensAreCappedBeforeForwarding() throws IOException {
        // @formatter:off
        given()
            .contentType("application/json")
            .header("x-api-key", "test-key")
            .header("anthropic-version", "2023-06-01")
            .body(readFileFromBaseDir("max-output.json"))
        .when()
            .post(LOCALHOST_2000 + "/v1/messages")
        .then()
            .statusCode(200);
        // @formatter:on

        assertThat(from(lastRequestBody).getInt("max_tokens"), equalTo(200));
    }
}
