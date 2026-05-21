package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.interceptor.ai.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser.SSEEvent;

import java.util.Set;

public interface LLMResponse {

    boolean isError();

    Usage getUsage();

    Set<String> getTerminalEvents();

    void process(SSEEvent event);

}
