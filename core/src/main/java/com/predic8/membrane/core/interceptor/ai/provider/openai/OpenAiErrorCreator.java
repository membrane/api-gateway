package com.predic8.membrane.core.interceptor.ai.provider.openai;

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.ai.provider.AbstractLLMErrorCreator;

import java.util.Collection;

import static com.predic8.membrane.core.http.Header.WWW_AUTHENTICATE;
import static com.predic8.membrane.core.http.Response.*;

public class OpenAiErrorCreator extends AbstractLLMErrorCreator {

    @Override
    public Response invalidRequestError(String message) {
        return Response.badRequest().json(envelope(message, "invalid_request_error", null, "bad_request")).build();
    }

    public Response tokenLimitExceeded(long tokenRequired, long tokenRemaining, long tokenResetInSeconds) {
        return statusCode(429).json(envelope(
                                "Token rate limit exceeded. Request requires %d tokens but only %d remain. Please wait %d seconds before retrying.".formatted(tokenRequired, tokenRemaining, tokenResetInSeconds),
                                "rate_limit_error",
                                null,
                                "token_limit_exceeded")).build();
    }

    public Response modelNotAllowed(String model, Collection<String> allowedModels) {
        return badRequest().json(envelope(
                        "Model '%s' is not allowed. Allowed models: %s."
                                .formatted(model, String.join(", ", allowedModels)),
                        "invalid_request_error",
                        null,
                        "model_not_allowed")).build();
    }

    public Response authenticationFailed() {
        return unauthorized().header(WWW_AUTHENTICATE, "Bearer").json(envelope(
                        "Invalid authentication credentials",
                        "invalid_request_error",
                        null,
                        "invalid_authentication")).build();
    }

    public Response contextLengthExceeded(long maxTokens, long estimatedTokens) {
        return badRequest().json(envelope(
                """
                        This model's maximum context length is %d tokens.
                        Your request contains approximately %d tokens.
                        """.formatted(maxTokens, estimatedTokens).trim(),
                "invalid_request_error",
                "input",
                "context_length_exceeded")).build();
    }
}
