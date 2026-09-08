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
package com.predic8.membrane.core.interceptor.llmgateway.provider.claude;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMResponseTest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

class ClaudeLLMResponseTest extends AbstractLLMResponseTest {

    @Override
    protected String url() {
        return "http://localhost/v1/messages";
    }

    @Override
    protected AbstractLLMResponse createResponse(Exchange exchange) {
        return new ClaudeLLMResponse(exchange, processed::add);
    }

    /**
     * The usage of a streamed answer arrives with the message_delta event, message_stop ends the
     * stream.
     */
    @Test
    void usageOfStreamedResponseIsReportedOnce() throws URISyntaxException {
        stream("""
                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":11,"output_tokens":7}}

                event: message_stop
                data: {"type":"message_stop"}

                """);

        assertUsage(new Usage(11, 7, 18));
    }

    /**
     * Cache tokens are billed, so they count towards the input.
     */
    @Test
    void cacheTokensOfStreamedResponseCountAsInput() throws URISyntaxException {
        stream("""
                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":10,"output_tokens":5,"cache_creation_input_tokens":3,"cache_read_input_tokens":2}}

                event: message_stop
                data: {"type":"message_stop"}

                """);

        assertUsage(new Usage(15, 5, 20));
    }

    /**
     * Anthropic reports the input of a streamed answer in message_start and the output in
     * message_delta, so neither event alone is the usage of the response.
     */
    @Test
    void inputOfStreamedResponseIsTakenFromMessageStart() throws URISyntaxException {
        stream("""
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_1","usage":{"input_tokens":25,"output_tokens":1}}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":15}}

                event: message_stop
                data: {"type":"message_stop"}

                """);

        assertUsage(new Usage(25, 15, 40));
    }

    /**
     * Cache tokens are billed, so the input reported by message_start includes them.
     */
    @Test
    void cacheTokensOfMessageStartCountAsInput() throws URISyntaxException {
        stream("""
                event: message_start
                data: {"type":"message_start","message":{"usage":{"input_tokens":10,"cache_creation_input_tokens":100,"cache_read_input_tokens":50}}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":15}}

                event: message_stop
                data: {"type":"message_stop"}

                """);

        assertUsage(new Usage(160, 15, 175));
    }

    /**
     * A model that repeats the input in message_delta wins over what message_start reported.
     */
    @Test
    void inputOfMessageDeltaWinsOverMessageStart() throws URISyntaxException {
        stream("""
                event: message_start
                data: {"type":"message_start","message":{"usage":{"input_tokens":25}}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":30,"output_tokens":15}}

                event: message_stop
                data: {"type":"message_stop"}

                """);

        assertUsage(new Usage(30, 15, 45));
    }

    @Test
    void usageOfNonStreamedResponseIsReportedOnce() throws URISyntaxException {
        newResponse(withJsonResponse("""
                {"usage":{"input_tokens":5,"output_tokens":6}}"""));

        assertUsage(new Usage(5, 6, 11));
    }

    /**
     * The cache tokens of a complete response are billed the same way as those of a streamed one.
     */
    @Test
    void cacheTokensOfNonStreamedResponseCountAsInput() throws URISyntaxException {
        newResponse(withJsonResponse("""
                {"usage":{"input_tokens":10,"cache_creation_input_tokens":100,"cache_read_input_tokens":50,"output_tokens":6}}"""));

        assertUsage(new Usage(160, 6, 166));
    }
}
