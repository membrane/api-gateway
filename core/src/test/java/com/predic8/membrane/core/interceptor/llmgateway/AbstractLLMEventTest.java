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

import com.predic8.membrane.core.util.http.SSEParser.SSEEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AbstractLLMEventTest {

    /**
     * Data that is not JSON, not a JSON object, or an object of no known type.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "not json at all",
            "[1,2,3]",
            """
            {"object":"something.else"}"""
    })
    void unknownDataYieldsNoEvent(String data) {
        assertNull(AbstractLLMEvent.create(new SSEEvent("message", data)));
    }

    @Test
    void doneMarkerYieldsDoneEvent() {
        var event = AbstractLLMEvent.create(new SSEEvent(null, "[DONE]"));

        assertInstanceOf(ChatCompletionDoneEvent.class, event);
        assertEquals("chat.completion.done", event.getType());
    }

    @Test
    void chatCompletionChunkYieldsChatCompletionEvent() {
        var event = AbstractLLMEvent.create(new SSEEvent(null, """
                {"object":"chat.completion.chunk","choices":[]}"""));

        assertInstanceOf(ChatCompletionEvent.class, event);
        assertEquals("chat.completion.chunk", event.getType());
    }

    @Test
    void typedEventYieldsResponsesApiEvent() {
        var event = AbstractLLMEvent.create(new SSEEvent("response.completed", """
                {"type":"response.completed"}"""));

        assertInstanceOf(ResponsesApiEvent.class, event);
        assertEquals("response.completed", event.getType());
    }
}
