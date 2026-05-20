package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.ai.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.ai.provider.claude.ClaudeErrorResponse.ClaudeError;

import java.util.Collection;
import java.util.UUID;

import static com.predic8.membrane.core.http.Response.badRequest;

public class ClaudeErrorCreator implements LLMErrorCreator {

    // Claude error types
    private static final String RATE_LIMIT_ERROR = "rate_limit_error";

    @Override
    public Response invalidRequestError(String message) {
        return null;
    }

    @Override
    public Response tokenLimitExceeded(long tokenRequired, long tokenRemaining, long tokenResetInSeconds) {
        return null;
    }

    @Override
    public Response modelNotAllowed(String model, Collection<String> allowedModels) {
        return null;
    }

    @Override
    public Response authenticationFailed() {
        return null;
    }

    @Override
    public Response inputTokensExceeded(long maxTokens, long estimatedTokens) {
        var json = ClaudeErrorResponse.builder().error(
                        ClaudeError.builder().type(RATE_LIMIT_ERROR)
                                .message("""
                                    prompt is too long:
                                    %d tokens > %d maximum
                                    """.formatted(estimatedTokens, maxTokens).trim())
                ).requestId("membrane_" + UUID.randomUUID())
                .toJson();

        return badRequest()
                .json(json)
                .build();
    }
}
