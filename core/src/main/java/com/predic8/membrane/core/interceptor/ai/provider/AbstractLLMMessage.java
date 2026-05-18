package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.exchange.Exchange;

public class AbstractLLMMessage {

    protected final Exchange exchange;

    public enum API { COMPLETIONS, NORMAL }

    protected API api;

    protected AbstractLLMMessage(Exchange exchange) {
        this.exchange = exchange;
        api = getAPI(exchange);
    }

    protected API getAPI(Exchange exchange) {
        if (exchange.getRequest().getUri().contains("/chat/completions")) {
            return API.COMPLETIONS;
        } else {
            return API.NORMAL;
        }
    }
}
