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
package com.predic8.membrane.core.interceptor.llmgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.ModelInputRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeLLMRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.google.GoogleLLMRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.OpenAiLLMResponsesRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.llmgateway.SystemPrompt.Action.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How each action rewrites the system prompt the client sent. Claude is used as the wire format
 * because it keeps the prompt in one field.
 */
class SystemPromptTest {

    private static final String MESSAGES = "http://localhost/v1/messages";
    private static final String CHAT_COMPLETIONS = "http://localhost/v1/chat/completions";
    private static final String RESPONSES = "http://localhost/v1/responses";
    private static final String GENERATE_CONTENT = "http://localhost/v1beta/models/m:generateContent";

    @Test
    void overwriteReplacesTheClientPrompt() throws Exception {
        assertJson("\"configured\"", apply(OVERWRITE, "configured", "from client"));
    }

    @Test
    void overwriteIsTheDefaultAction() {
        assertEquals(OVERWRITE, new SystemPrompt().getAction());
    }

    /**
     * The client's string prompt becomes a text block of its own next to the configured one.
     */
    @Test
    void prependPutsTheConfiguredPromptFirst() throws Exception {
        assertJson("""
                [{"type":"text","text":"configured"},{"type":"text","text":"from client"}]""",
                apply(PREPEND, "configured", "from client"));
    }

    @Test
    void appendPutsTheConfiguredPromptLast() throws Exception {
        assertJson("""
                [{"type":"text","text":"from client"},{"type":"text","text":"configured"}]""",
                apply(APPEND, "configured", "from client"));
    }

    @Test
    void removeDropsThePrompt() throws Exception {
        assertTrue(apply(REMOVE, "configured", "from client").isMissingNode());
    }

    /**
     * A request without a system prompt still gets the configured one, and nothing else.
     */
    @Test
    void combiningWithAnAbsentClientPromptAddsOnlyTheConfiguredOne() throws Exception {
        assertJson("\"configured\"", apply(OVERWRITE, "configured", null));
        assertJson("""
                [{"type":"text","text":"configured"}]""", apply(PREPEND, "configured", null));
        assertJson("""
                [{"type":"text","text":"configured"}]""", apply(APPEND, "configured", null));
    }

    /**
     * Clients send their system prompt in every form the provider accepts. Prepending or appending
     * adds the configured prompt next to it and leaves the client's prompt as it is, including
     * attributes like Claude's cache_control.
     */
    @Nested
    class KeepsTheClientPromptInItsWireFormat {

        @Test
        void claudeTextBlocksArePrepended() throws Exception {
            var request = new ClaudeLLMRequest(exchange(MESSAGES, """
                    {"system":[{"type":"text","text":"from client","cache_control":{"type":"ephemeral"}}]}"""));

            handle(PREPEND, request);

            assertJson("""
                    [{"type":"text","text":"configured"},
                     {"type":"text","text":"from client","cache_control":{"type":"ephemeral"}}]""",
                    request.getJson().path("system"));
        }

        @Test
        void claudeTextBlocksAreAppended() throws Exception {
            var request = new ClaudeLLMRequest(exchange(MESSAGES, """
                    {"system":[{"type":"text","text":"from client","cache_control":{"type":"ephemeral"}}]}"""));

            handle(APPEND, request);

            assertJson("""
                    [{"type":"text","text":"from client","cache_control":{"type":"ephemeral"}},
                     {"type":"text","text":"configured"}]""",
                    request.getJson().path("system"));
        }

        @Test
        void chatCompletionsSystemMessageWithContentPartsIsKept() throws Exception {
            var request = new ChatCompletionsRequest(exchange(CHAT_COMPLETIONS, """
                    {"messages":[{"role":"system","content":[{"type":"text","text":"from client"}]},
                                 {"role":"user","content":"Hi"}]}"""));

            handle(PREPEND, request);

            assertJson("""
                    [{"role":"system","content":"configured"},
                     {"role":"system","content":[{"type":"text","text":"from client"}]},
                     {"role":"user","content":"Hi"}]""",
                    request.getJson().path("messages"));
        }

        @Test
        void chatCompletionsAppendsAfterAllSystemMessages() throws Exception {
            var request = new ChatCompletionsRequest(exchange(CHAT_COMPLETIONS, """
                    {"messages":[{"role":"system","content":"one"},
                                 {"role":"system","content":"two"},
                                 {"role":"user","content":"Hi"}]}"""));

            handle(APPEND, request);

            assertJson("""
                    [{"role":"system","content":"one"},
                     {"role":"system","content":"two"},
                     {"role":"system","content":"configured"},
                     {"role":"user","content":"Hi"}]""",
                    request.getJson().path("messages"));
        }

        /**
         * Newer OpenAI models take the system prompt as a developer message.
         */
        @Test
        void chatCompletionsOverwriteReplacesDeveloperMessages() throws Exception {
            var request = new ChatCompletionsRequest(exchange(CHAT_COMPLETIONS, """
                    {"messages":[{"role":"developer","content":"from client"},
                                 {"role":"user","content":"Hi"}]}"""));

            handle(OVERWRITE, request);

            assertJson("""
                    [{"role":"system","content":"configured"},
                     {"role":"user","content":"Hi"}]""",
                    request.getJson().path("messages"));
        }

        /**
         * In the Responses API, system and developer messages in {@code input} act as system prompts.
         */
        @ParameterizedTest
        @EnumSource(value = SystemPrompt.Action.class, names = {"OVERWRITE", "REMOVE"})
        void responsesSystemAndDeveloperInputItemsAreRemoved(SystemPrompt.Action action) throws Exception {
            var request = new OpenAiLLMResponsesRequest(exchange(RESPONSES, """
                    {"instructions":"from client",
                     "input":[{"role":"developer","content":"from client"},
                              {"type":"message","role":"system","content":[{"type":"input_text","text":"from client"}]},
                              {"role":"user","content":"Hi"}]}"""));

            handle(action, request);

            assertJson("""
                    [{"role":"user","content":"Hi"}]""",
                    request.getJson().path("input"));
            assertEquals(action == OVERWRITE ? "configured" : "", request.getJson().path("instructions").asText(""));
        }

        @Test
        void responsesInputItemsAreKeptOnPrepend() throws Exception {
            var request = new OpenAiLLMResponsesRequest(exchange(RESPONSES, """
                    {"input":[{"role":"developer","content":"from client"},{"role":"user","content":"Hi"}]}"""));

            handle(PREPEND, request);

            assertEquals(2, request.getJson().path("input").size());
        }

        /**
         * The Gemini API also accepts the proto field name {@code system_instruction}.
         */
        @ParameterizedTest
        @EnumSource(value = SystemPrompt.Action.class, names = {"OVERWRITE", "REMOVE"})
        void geminiSnakeCaseSystemInstructionIsReplaced(SystemPrompt.Action action) throws Exception {
            var request = new GoogleLLMRequest(exchange(GENERATE_CONTENT, """
                    {"system_instruction":{"parts":[{"text":"from client"}]}}"""));

            handle(action, request);

            assertFalse(request.getJson().has("system_instruction"));
            assertEquals(action == OVERWRITE ? 1 : 0, request.getJson().path("systemInstruction").path("parts").size());
        }

        @Test
        void geminiSnakeCaseSystemInstructionIsKeptOnPrepend() throws Exception {
            var request = new GoogleLLMRequest(exchange(GENERATE_CONTENT, """
                    {"system_instruction":{"parts":[{"text":"one"}]}}"""));

            handle(PREPEND, request);

            assertJson("""
                    {"systemInstruction":{"parts":[{"text":"configured"},{"text":"one"}]}}""",
                    request.getJson());
        }

        @Test
        void geminiPartsAreAllKept() throws Exception {
            var request = new GoogleLLMRequest(exchange(GENERATE_CONTENT, """
                    {"systemInstruction":{"parts":[{"text":"one"},{"text":"two"}]}}"""));

            handle(PREPEND, request);

            assertJson("""
                    [{"text":"configured"},{"text":"one"},{"text":"two"}]""",
                    request.getJson().path("systemInstruction").path("parts"));
        }

        @Test
        void geminiPartsAreAllKeptOnAppend() throws Exception {
            var request = new GoogleLLMRequest(exchange(GENERATE_CONTENT, """
                    {"systemInstruction":{"parts":[{"text":"one"},{"text":"two"}]}}"""));

            handle(APPEND, request);

            assertJson("""
                    [{"text":"one"},{"text":"two"},{"text":"configured"}]""",
                    request.getJson().path("systemInstruction").path("parts"));
        }

        /**
         * Providers reject empty text parts and messages, so an empty prompt must leave the client's
         * system prompt as it was sent.
         */
        @ParameterizedTest(name = "{0} {1}: {2}")
        @CsvSource(delimiter = '|', value = {
                "PREPEND | " + GENERATE_CONTENT + " | {}",
                "APPEND  | " + GENERATE_CONTENT + " | {}",
                "PREPEND | " + GENERATE_CONTENT + " | {\"systemInstruction\":{\"parts\":[{\"text\":\"client\"}]}}",
                "APPEND  | " + GENERATE_CONTENT + " | {\"systemInstruction\":{\"parts\":[{\"text\":\"client\"}]}}",
                "PREPEND | " + CHAT_COMPLETIONS + " | {\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}",
                "APPEND  | " + CHAT_COMPLETIONS + " | {\"messages\":[{\"role\":\"system\",\"content\":\"client\"},{\"role\":\"user\",\"content\":\"Hi\"}]}",
                "PREPEND | " + RESPONSES + " | {}",
                "APPEND  | " + RESPONSES + " | {}",
                "APPEND  | " + RESPONSES + " | {\"instructions\":\"client\"}",
        })
        void emptyPromptLeavesClientPromptUnchanged(SystemPrompt.Action action, String url, String body) throws Exception {
            final var request = switch (url) {
                case GENERATE_CONTENT -> new GoogleLLMRequest(exchange(url, body));
                case CHAT_COMPLETIONS -> new ChatCompletionsRequest(exchange(url, body));
                case RESPONSES -> new OpenAiLLMResponsesRequest(exchange(url, body));
                default -> throw new IllegalArgumentException(url);
            };
            final var before = request.getJson().deepCopy();

            final var systemPrompt = new SystemPrompt();
            systemPrompt.setAction(action);
            assertEquals(CONTINUE, systemPrompt.handleRequest(request, new Exchange(null)));

            assertEquals(before, request.getJson());
        }

        @Test
        void responsesInstructionsWithoutClientPromptHaveNoStraySeparator() throws Exception {
            var request = new OpenAiLLMResponsesRequest(exchange(RESPONSES, "{}"));

            handle(APPEND, request);

            assertEquals("configured", request.getJson().path("instructions").asText());
        }

        private static void handle(SystemPrompt.Action action, ModelInputRequest request) {
            var systemPrompt = new SystemPrompt();
            systemPrompt.setAction(action);
            systemPrompt.setContent("configured");
            assertEquals(CONTINUE, systemPrompt.handleRequest(request, new Exchange(null)));
        }
    }

    private static void assertJson(String expected, JsonNode actual) throws Exception {
        assertEquals(new ObjectMapper().readTree(expected), actual);
    }

    private static Exchange exchange(String url, String body) throws Exception {
        return post(url).json(body).buildExchange();
    }

    /**
     * @return the {@code system} field of the rewritten Claude request
     */
    private static JsonNode apply(SystemPrompt.Action action, String content, String clientPrompt) throws Exception {
        var systemPrompt = new SystemPrompt();
        systemPrompt.setAction(action);
        systemPrompt.setContent(content);

        final var request = new ClaudeLLMRequest(exchange(clientPrompt));

        assertEquals(CONTINUE, systemPrompt.handleRequest(request, new Exchange(null)));

        return request.getJson().path("system");
    }

    private static Exchange exchange(String clientPrompt) throws Exception {
        var body = clientPrompt == null ? "{}" : "{\"system\":\"" + clientPrompt + "\"}";
        return post("http://localhost/v1/messages").json(body).buildExchange();
    }
}
