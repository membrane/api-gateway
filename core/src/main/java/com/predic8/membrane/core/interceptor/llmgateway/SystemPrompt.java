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
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description When used with older chat completions API the instruction is converted to a system message like:
 * "system": "You are a helpful assistant."
 */
@MCElement(name = "systemPrompt")
public class SystemPrompt {

    private static final Logger log = LoggerFactory.getLogger(SystemPrompt.class);

    public enum Action {
        REMOVE, OVERWRITE, APPEND, PREPEND
    }

    private Action action;
    private String content = "";

    public Outcome handleRequest(LLMRequest aiReq, Exchange exc) {
        var instructions = aiReq.getSystemPrompt() == null ? "" : aiReq.getSystemPrompt();
        switch (action) {
            case OVERWRITE -> {
                log.debug("Overwriting instructions: {}", content);
                aiReq.setSystemPrompt(content);
            }
            case PREPEND -> {
                log.debug("Prepending instructions: {}", content);
                aiReq.setSystemPrompt( content + "\n" + instructions);
            }
            case APPEND -> {
                log.debug("Appending instructions: {}", content);
                aiReq.setSystemPrompt(instructions + "\n" + content);
            }
            case REMOVE -> {
                log.info("Removing instructions: {}", instructions);
                aiReq.removeSystemPrompt();
            }
        }
        return CONTINUE;
    }

    public Action getAction() {
        return action;
    }

    @MCAttribute
    public void setAction(Action action) {
        this.action = action;
    }

    public String getContent() {
        return content;
    }

    @MCAttribute
    public void setContent(String content) {
        this.content = content;
    }
}
