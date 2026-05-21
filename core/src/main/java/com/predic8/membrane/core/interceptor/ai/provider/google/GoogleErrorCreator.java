package com.predic8.membrane.core.interceptor.ai.provider.google;

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.ai.provider.AbstractLLMErrorCreator;

import java.util.Collection;

import static com.predic8.membrane.core.http.Header.WWW_AUTHENTICATE;
import static com.predic8.membrane.core.http.Response.*;

public class GoogleErrorCreator extends AbstractLLMErrorCreator {

    @Override
    public Response invalidRequestError(String message) {
        return badRequest().json(
                envelope(400, message, "INVALID_ARGUMENT")
        ).build();
    }

    public Response tokenLimitExceeded(long tokenRequired,
                                       long tokenRemaining,
                                       long tokenResetInSeconds) {

        return statusCode(429).json(
                envelope(
                        429,
                        """
                        Token rate limit exceeded.
                        Request requires %d tokens but only %d remain.
                        Retry after %d seconds.
                        """
                                .formatted(tokenRequired, tokenRemaining, tokenResetInSeconds)
                                .trim(),
                        "RESOURCE_EXHAUSTED"
                )
        ).build();
    }

    public Response modelNotAllowed(String model,
                                    Collection<String> allowedModels) {

        return badRequest().json(
                envelope(
                        400,
                        "Model '%s' is not allowed. Allowed models: %s."
                                .formatted(model, String.join(", ", allowedModels)),
                        "INVALID_ARGUMENT"
                )
        ).build();
    }

    public Response authenticationFailed() {
        return unauthorized()
                .header(WWW_AUTHENTICATE, "Bearer")
                .json(
                        envelope(
                                401,
                                "Invalid API key.",
                                "UNAUTHENTICATED"
                        )
                ).build();
    }

    public Response inputTokensExceeded(long maxTokens,
                                        long estimatedTokens) {

        return badRequest().json(
                envelope(
                        400,
                        """
                        The input token count (%d) exceeds the maximum allowed (%d).
                        """
                                .formatted(estimatedTokens, maxTokens)
                                .trim(),
                        "INVALID_ARGUMENT"
                )
        ).build();
    }

    private String envelope(int code,
                            String message,
                            String status) {

        return createJson(new ErrorEnvelope(
                new ErrorBody(code, message, status)
        ));
    }

    private record ErrorEnvelope(ErrorBody error) {
    }

    private record ErrorBody(
            int code,
            String message,
            String status
    ) {
    }
}
