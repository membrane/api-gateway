package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.http.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ChatCompletionsSSEParser {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsSSEParser.class);

    private final StringBuilder buffer = new StringBuilder();
    private final List<ChatCompletionChunk> chunks = new ArrayList<>();
    private final StringBuilder data = new StringBuilder();

    private boolean done;

    public boolean parse(Chunk chunk) {
        if (done)
            return true;

        log.debug("Parsing chat completions SSE chunk: {}", chunk);

        buffer.append(chunk.toString());

        int lineEnd;
        while ((lineEnd = findLineEnd(buffer)) >= 0) {
            String line = readLine(buffer, lineEnd);

            if (line.isEmpty()) {
                ChatCompletionChunk parsedChunk = buildChunk();
                resetEvent();

                if (parsedChunk != null) {
                    if (parsedChunk.isDone()) {
                        done = true;
                        return true;
                    }

                    chunks.add(parsedChunk);
                }

                continue;
            }

            parseLine(line);
        }

        return false;
    }

    public List<ChatCompletionChunk> getChunks() {
        return List.copyOf(chunks);
    }

    public Optional<ChatCompletionChunk> getLastChunk() {
        if (chunks.isEmpty())
            return Optional.empty();

        return Optional.of(chunks.get(chunks.size() - 1));
    }

    public boolean isDone() {
        return done;
    }

    private ChatCompletionChunk buildChunk() {
        if (data.isEmpty())
            return null;

        String value = data.toString();

        if ("[DONE]".equals(value))
            return ChatCompletionChunk.done();

        return ChatCompletionChunk.json(value);
    }

    private void resetEvent() {
        data.setLength(0);
    }

    private void parseLine(String line) {
        if (line.startsWith(":"))
            return;

        int colon = line.indexOf(':');

        String field = colon >= 0 ? line.substring(0, colon) : line;
        String value = colon >= 0 ? line.substring(colon + 1) : "";

        if (value.startsWith(" "))
            value = value.substring(1);

        if ("data".equals(field)) {
            if (!data.isEmpty())
                data.append('\n');

            data.append(value);
        }
    }

    private static int findLineEnd(StringBuilder buffer) {
        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);

            if (c == '\n' || c == '\r')
                return i;
        }

        return -1;
    }

    private static String readLine(StringBuilder buffer, int lineEnd) {
        String line = buffer.substring(0, lineEnd);

        int removeUntil = lineEnd + 1;

        if (lineEnd + 1 < buffer.length()
                && buffer.charAt(lineEnd) == '\r'
                && buffer.charAt(lineEnd + 1) == '\n') {
            removeUntil++;
        }

        buffer.delete(0, removeUntil);
        return line;
    }

    public static final class ChatCompletionChunk {

        private static final ObjectMapper om = new ObjectMapper();

        private final boolean done;
        private final String data;
        private ObjectNode json;

        private ChatCompletionChunk(boolean done, String data) {
            this.done = done;
            this.data = data;
        }

        public static ChatCompletionChunk done() {
            return new ChatCompletionChunk(true, null);
        }

        public static ChatCompletionChunk json(String data) {
            return new ChatCompletionChunk(false, data);
        }

        public boolean isDone() {
            return done;
        }

        public String getData() {
            return data;
        }

        public ObjectNode json() {
            if (done)
                throw new IllegalStateException("[DONE] has no JSON body.");

            if (json != null)
                return json;

            try {
                json = (ObjectNode) om.readTree(data);
                return json;
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Could not parse chat completion chunk JSON.", e);
            }
        }

        public String contentDelta() {
            if (done)
                return null;

            return json()
                    .path("choices")
                    .path(0)
                    .path("delta")
                    .path("content")
                    .asText(null);
        }

        public boolean hasToolCalls() {
            if (done)
                return false;

            return json()
                    .path("choices")
                    .path(0)
                    .path("delta")
                    .path("tool_calls")
                    .isArray();
        }
    }
}