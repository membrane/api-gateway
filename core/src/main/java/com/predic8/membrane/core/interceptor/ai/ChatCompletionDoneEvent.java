package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.node.NullNode;

public class ChatCompletionDoneEvent extends AbstractLLMEvent {

    public ChatCompletionDoneEvent() {
        super(NullNode.getInstance());
    }

    @Override
    public String getType() {
        return "chat.completion.done";
    }
}