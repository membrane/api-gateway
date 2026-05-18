package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.provider.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.ai.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.ai.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser.SSEEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

public class ClaudeLLMResponse extends AbstractLLMResponse {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLLMResponse.class);

    private Usage usage;

    private StringBuffer inputJson = new StringBuffer();

    private String tool;

    public ClaudeLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange,postProcessor);
    }

    @Override
    public Set<String> getTerminalEvents() {
        return Set.of("message_stop");
    }

    @Override
    public void process(SSEEvent event) {
        log.debug("Event: {}", event);

        if ("content_block_start".equals(event.name())) {
            var cbs = ContentBlockStart.from(event.json());
            if (cbs.getToolUse() != null) {
                tool = cbs.getToolUse().getName();
            }
        }
        if ("message_delta".equals(event.name())) {
            var md = MessageDelta.from(event.json());
            log.debug("Message delta: {}", md);
            if (md.getUsage() != null) {
                usage = md.getUsage();
                if (tool != null)
                    log.debug("Tool {} with {}", tool, inputJson.toString());
            }
        }
        if ("content_block_delta".equals(event.name())) {
            var cbd = ContentBlockDelta.from(event.json());
            if (cbd.isInputJsonDelta()) {
                inputJson.append(cbd.getPartialJson());
            }
        }
    }

    @Override
    public Usage getUsage() {
        return usage;
    }
}