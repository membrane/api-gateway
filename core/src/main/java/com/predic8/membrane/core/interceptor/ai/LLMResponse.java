package com.predic8.membrane.core.interceptor.ai;

import com.predic8.membrane.core.interceptor.ai.store.Usage;

public interface LLMResponse {

    boolean isError();

    Usage getUsage();

}
