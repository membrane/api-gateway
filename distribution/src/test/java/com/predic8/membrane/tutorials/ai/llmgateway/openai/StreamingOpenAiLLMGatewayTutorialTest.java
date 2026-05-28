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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.restassured.path.json.JsonPath.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the streaming (SSE) path of
 * {@code distribution/tutorials/ai/llm-gateway/openai/10-Basic-LLM-Gateway.yaml}.
 *
 * <p>The mock upstream returns {@code Content-Type: text/event-stream} with three
 * SSE events so the gateway's SSE processing path is exercised end-to-end without
 * a real OpenAI connection:
 *
 * <ul>
 *   <li>{@code response.created}     — initial acknowledgement</li>
 *   <li>{@code response.output_text.delta} — incremental text chunk</li>
 *   <li>{@code response.completed}   — terminal event carrying usage statistics</li>
 * </ul>
 *
 * <p>Because RestAssured does not handle server-sent events well, these tests use the
 * Java {@link java.net.http.HttpClient} directly — the same approach used in
 * {@code ServerSentEventsTutorialTest}.
 */
public class StreamingOpenAiLLMGatewayTutorialTest extends AbstractOpenAiTutorialTest {

    private static final String RESPONSES_ENDPOINT = LOCALHOST_2000 + "/v1/responses";

    @Override
    protected String getTutorialYaml() {
        return "10-Basic-LLM-Gateway.yaml";
    }

    /** Tell the mock server to respond as a finite SSE stream. */
    @Override
    protected String mockContentType() {
        return "text/event-stream";
    }

    /**
     * A minimal but complete SSE body: one delta event followed by the terminal
     * {@code response.completed} event that carries the usage node the gateway
     * reads for token accounting.
     */
    @Override
    protected String mockResponse() {
        return """
                event: response.created
                data: {"type":"response.created","response":{"id":"resp_mock","object":"response","status":"in_progress","model":"gpt-5-nano"}}

                event: response.output_text.delta
                data: {"type":"response.output_text.delta","item_id":"msg_mock","output_index":0,"content_index":0,"delta":"I am a mock."}

                event: response.completed
                data: {"type":"response.completed","response":{"id":"resp_mock","object":"response","status":"completed","model":"gpt-5-nano","output":[{"type":"message","id":"msg_mock","status":"completed","role":"assistant","content":[{"type":"output_text","text":"I am a mock."}]}],"usage":{"input_tokens":50,"output_tokens":50,"total_tokens":100}}}

                """;
    }

    /**
     * The gateway must forward a streaming request and pass the {@code text/event-stream}
     * response through to the client intact. The response body must contain the SSE events
     * emitted by the upstream, including the delta text.
     */
    @Test
    void streamingResponseIsForwarded() throws IOException, InterruptedException {
        var response = sendStreamingRequest("stream.json");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/event-stream"),
                "Expected Content-Type text/event-stream");
        assertTrue(response.body().contains("response.output_text.delta"),
                "SSE body must contain the delta event name");
        assertTrue(response.body().contains("I am a mock."),
                "SSE body must contain the delta text");
        assertTrue(response.body().contains("response.completed"),
                "SSE body must contain the terminal event");
    }

    /**
     * When the request carries {@code "max_output_tokens": 500} and the gateway is
     * configured with {@code maxOutputTokens: 200}, the gateway must rewrite the field
     * to 200 before forwarding — even for streaming requests.
     *
     * <p>The mock captures the forwarded request body so we can assert the capped value.
     */
    @Test
    void streamingOutputTokensAreCappedBeforeForwarding() throws IOException, InterruptedException {
        var response = sendStreamingRequest("max-output-stream.json");

        assertEquals(200, response.statusCode());
        assertThat(from(lastRequestBody).getInt("max_output_tokens"), equalTo(200));
    }

    // -------------------------------------------------------------------------

    private HttpResponse<String> sendStreamingRequest(String fixture) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(RESPONSES_ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer test-key")
                .POST(HttpRequest.BodyPublishers.ofString(readFileFromBaseDir(fixture)))
                .build();

        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
