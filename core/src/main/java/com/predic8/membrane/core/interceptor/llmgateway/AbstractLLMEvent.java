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
import com.predic8.membrane.core.util.http.SSEParser;
import com.predic8.membrane.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLLMEvent {

    private static final Logger log = LoggerFactory.getLogger(AbstractLLMEvent.class);

    protected static final ObjectMapper om = new ObjectMapper();

    protected final JsonNode json;

    protected AbstractLLMEvent(JsonNode json) {
        this.json = json;
    }

    public abstract String getType();

    public JsonNode getJson() {
        return json;
    }

    public static AbstractLLMEvent create(SSEParser.SSEEvent sse) {

        if ("[DONE]".equals(sse.data())) {
            return new ChatCompletionDoneEvent();
        }

        var opt = JsonUtil.getJsonObject(sse.data());
        if (opt.isEmpty()) {
            log.info("Unknown event format: {}", sse.data());
            throw new RuntimeException("Unknown event format: " + sse.data());
        }

        var json = opt.get();

        // Responses API
        if (json.has("type")) {
            return new ResponsesApiEvent(json);
        }

        // Chat Completions API
        if ("chat.completion.chunk".equals(json.path("object").asText())) {
            return new ChatCompletionEvent(json);
        }

        log.debug("Unknown event format: {}", json);

        return null;
    }
}
