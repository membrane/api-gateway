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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.ModelInputRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeLLMRequest;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.llmgateway.SystemPrompt.Action.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How each action rewrites the system prompt the client sent. Claude is used as the wire format
 * because it keeps the prompt in one field.
 */
class SystemPromptTest {

    @Test
    void overwriteReplacesTheClientPrompt() throws Exception {
        assertEquals("configured", apply(OVERWRITE, "configured", "from client"));
    }

    @Test
    void overwriteIsTheDefaultAction() {
        assertEquals(OVERWRITE, new SystemPrompt().getAction());
    }

    @Test
    void prependPutsTheConfiguredPromptFirst() throws Exception {
        assertEquals("configured\nfrom client", apply(PREPEND, "configured", "from client"));
    }

    @Test
    void appendPutsTheConfiguredPromptLast() throws Exception {
        assertEquals("from client\nconfigured", apply(APPEND, "configured", "from client"));
    }

    @Test
    void removeDropsThePrompt() throws Exception {
        assertEquals("", apply(REMOVE, "configured", "from client"));
    }

    /**
     * A request without a system prompt still gets the configured one, without a stray newline for
     * overwrite and with an empty half for the combining actions.
     */
    @Test
    void combiningWithAnAbsentClientPromptKeepsTheSeparator() throws Exception {
        assertEquals("configured", apply(OVERWRITE, "configured", null));
        assertEquals("configured\n", apply(PREPEND, "configured", null));
        assertEquals("\nconfigured", apply(APPEND, "configured", null));
    }

    private static String apply(SystemPrompt.Action action, String content, String clientPrompt) throws Exception {
        var systemPrompt = new SystemPrompt();
        systemPrompt.setAction(action);
        systemPrompt.setContent(content);

        ModelInputRequest request = new ClaudeLLMRequest(exchange(clientPrompt));

        assertEquals(CONTINUE, systemPrompt.handleRequest(request, new Exchange(null)));

        return request.getSystemPrompt();
    }

    private static Exchange exchange(String clientPrompt) throws Exception {
        var body = clientPrompt == null ? "{}" : "{\"system\":\"" + clientPrompt + "\"}";
        return post("http://localhost/v1/messages").json(body).buildExchange();
    }
}
