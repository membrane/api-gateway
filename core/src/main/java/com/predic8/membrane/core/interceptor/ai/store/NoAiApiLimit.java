package com.predic8.membrane.core.interceptor.ai.store;

public class NoAiApiLimit extends AiApiLimit{

    @Override
    public long checkLimit() {
        return 1000;
    }


}
