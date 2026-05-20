package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.http.Response;

import java.util.Collection;

public interface LLMErrorCreator {

    Response invalidRequestError(String message);

    Response tokenLimitExceeded(long tokenRequired, long tokenRemaining, long tokenResetInSeconds);

    Response modelNotAllowed(String model, Collection<String> allowedModels);

    Response authenticationFailed();

    Response contextLengthExceeded(long maxTokens, long estimatedTokens);
}
