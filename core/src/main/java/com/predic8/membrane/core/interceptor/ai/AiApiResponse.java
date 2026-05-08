package com.predic8.membrane.core.interceptor.ai;

import com.predic8.membrane.core.interceptor.ai.store.Usage;

public interface AiApiResponse {

    boolean isError();

    Usage getUsage();
}
