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
 * @description When used with older chat completions API the instruction is converted to a system message like:
 * "system": "You are a helpful assistant."
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
