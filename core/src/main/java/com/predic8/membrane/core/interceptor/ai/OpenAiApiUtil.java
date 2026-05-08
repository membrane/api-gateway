package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.SSEUtil;

import java.util.Collection;

import static com.predic8.membrane.core.http.Header.WWW_AUTHENTICATE;
import static com.predic8.membrane.core.http.Response.badRequest;
import static com.predic8.membrane.core.http.Response.unauthorized;

public class OpenAiApiUtil {

    private static final ObjectMapper om = new ObjectMapper();

    /**
     * Checks if the SSE Event is a terminal event.
     * @param event SSE Event
     * @return
     */
    public static boolean terminalEvent(SSEUtil.SSEvent event) {
       return "response.completed".equals(event.name()) || "response.incomplete".equals(event.name());
    }

    public static Response modelNotAllowed(String model, Collection<String> allowedModels) {
        return badRequest().json(createJson(new ErrorEnvelope(
                new ErrorBody(
                        "Model '%s' is not allowed. Allowed models: %s."
                                .formatted(model, String.join(", ", allowedModels)),
                        "invalid_request_error",
                        null,
                        "model_not_allowed"
                )
        ))).build();
    }

    public static Response authenticationFailed() {
        return unauthorized().header(WWW_AUTHENTICATE, "Bearer").json(createJson(new ErrorEnvelope(
                new ErrorBody(
                        "Invalid authentication credentials",
                        "invalid_request_error",
                        null,
                        "invalid_authentication"
                )
        ))).build();
    }

    public static Response contextLengthExceeded(long maxTokens, long estimatedTokens) {
        return badRequest().json(createJson(new ErrorEnvelope(new ErrorBody(
                """
                        This model's maximum context length is %d tokens.
                        Your request contains approximately %d tokens.
                        """.formatted(maxTokens, estimatedTokens).trim(),
                "invalid_request_error",
                "input",
                "context_length_exceeded"
        )))).build();
    }

    public static Response tokenLimitExceeded() {
        return badRequest()
                .json(createJson(new ErrorEnvelope(
                        new ErrorBody(
                                "Token rate limit exceeded.",
                                "rate_limit_error",
                                null,
                                "token_limit_exceeded"
                        )
                )))
                .build();
    }

    public static String createJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return """
                    { "error": "Could not create JSON" }
                    """;
        }
    }

    record ErrorEnvelope(ErrorBody error) {
    }

    record ErrorBody(
            String message,
            String type,
            String param,
            String code
    ) {
    }
}
