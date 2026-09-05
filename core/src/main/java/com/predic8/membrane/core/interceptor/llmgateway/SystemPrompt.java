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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.llmgateway.provider.ModelInputRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Sets the system prompt of every request before it is forwarded, so that all clients of an api talk to
 * the model under the same instruction. The prompt is written in the wire format of the configured provider: the
 * <code>system</code> field for Claude, <code>instructions</code> for the OpenAI Responses API, a
 * <code>role: system</code> message for Chat Completions, and <code>systemInstruction</code> for Gemini. A prompt the
 * client sent is replaced, kept or dropped, depending on the action.
 * <pre>
 * systemPrompt:
 *   [ action: OVERWRITE | APPEND | PREPEND | REMOVE ]   # default: OVERWRITE
 *   [ content: &lt;prompt&gt; ]
 * </pre>
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - llmGateway:
 *         claude: {}
 *         systemPrompt:
 *           action: PREPEND
 *           content: Answer in German.
 *   target:
 *     url: https://api.anthropic.com
 * </code></pre>
 */
@MCElement(name = "systemPrompt")
public class SystemPrompt {

    private static final Logger log = LoggerFactory.getLogger(SystemPrompt.class);

    public enum Action {
        REMOVE, OVERWRITE, APPEND, PREPEND
    }

    private Action action = Action.OVERWRITE;
    private String content = "";

    public Outcome handleRequest(ModelInputRequest mir, Exchange exc) {
        var instructions = mir.getSystemPrompt() == null ? "" : mir.getSystemPrompt();
        switch (action) {
            case OVERWRITE -> {
                log.debug("Overwriting instructions: {}", content);
                mir.setSystemPrompts(List.of(content));
            }
            case PREPEND -> {
                log.debug("Prepending instructions: {}", content);
                mir.setSystemPrompts(List.of(content, instructions));
            }
            case APPEND -> {
                log.debug("Appending instructions: {}", content);
                mir.setSystemPrompts(List.of(instructions, content));
            }
            case REMOVE -> {
                log.info("Removing instructions: {}", instructions);
                mir.removeSystemPrompt();
            }
        }
        return CONTINUE;
    }

    public Action getAction() {
        return action;
    }

    /**
     * @description What to do with the system prompt the client sent. <code>OVERWRITE</code> replaces it,
     * <code>APPEND</code> and <code>PREPEND</code> combine both with a newline between them, and
     * <code>REMOVE</code> drops it and ignores the content.
     * @default OVERWRITE
     * @example PREPEND
     */
    @MCAttribute
    public void setAction(Action action) {
        this.action = action;
    }

    public String getContent() {
        return content;
    }

    /**
     * @description The system prompt to set. Ignored when the action is <code>REMOVE</code>.
     * @default (empty)
     * @example You are a helpful assistant. Answer in German.
     */
    @MCAttribute
    public void setContent(String content) {
        this.content = content;
    }
}
