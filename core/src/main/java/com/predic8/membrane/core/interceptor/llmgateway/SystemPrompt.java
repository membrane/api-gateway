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
    public static final String INSTRUCTIONS = "instructions";

    enum Action {
        REJECT, REMOVE, OVERWRITE, APPEND, PREPEND
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
