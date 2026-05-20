package com.predic8.membrane.core.interceptor.ai.store;

/**
 * @description Store that does not limit the number of AI API calls (experimental).
 */
public class NoAiApiLimit extends AiApiLimit{

    @Override
    public long checkLimit(long tokensForNextRequest) {
        return 1000; // Returns a value greater than 0 to indicate that the request can be processed.
    }
}
