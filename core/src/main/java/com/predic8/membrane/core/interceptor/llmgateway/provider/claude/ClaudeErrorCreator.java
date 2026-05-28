/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.llmgateway.provider.claude;

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeErrorResponse.ClaudeError;

import java.util.Collection;
import java.util.UUID;

import static com.predic8.membrane.core.http.Header.WWW_AUTHENTICATE;
import static com.predic8.membrane.core.http.Response.*;

public class ClaudeErrorCreator implements LLMErrorCreator {

    private static final String INVALID_REQUEST_ERROR = "invalid_request_error";
    private static final String AUTHENTICATION_ERROR = "authentication_error";
    private static final String RATE_LIMIT_ERROR = "rate_limit_error";

    @Override
    public Response invalidRequestError(String message) {
        return badRequest()
                .json(error(INVALID_REQUEST_ERROR, message))
                .build();
    }

    @Override
    public Response tokenLimitExceeded(long tokenRequired, long tokenRemaining, long tokenResetInSeconds) {
        long visibleRemaining = Math.max(0, tokenRemaining);

        return statusCode(429)
                .json(error(
                        RATE_LIMIT_ERROR,
                        """
                        Token rate limit exceeded.
                        Request requires %d tokens but only %d remain.
                        Retry after %d seconds.
                        """.formatted(tokenRequired, visibleRemaining, tokenResetInSeconds).trim()
                ))
                .build();
    }

    @Override
    public Response modelNotAllowed(String model, Collection<String> allowedModels) {
        return badRequest()
                .json(error(
                        INVALID_REQUEST_ERROR,
                        "Model '%s' is not allowed. Allowed models: %s."
                                .formatted(model, String.join(", ", allowedModels))
                ))
                .build();
    }

    @Override
    public Response authenticationFailed() {
        return unauthorized()
                .header(WWW_AUTHENTICATE, "Bearer")
                .json(error(AUTHENTICATION_ERROR, "Invalid bearer token"))
                .build();
    }

    @Override
    public Response inputTokensExceeded(long maxTokens, long estimatedTokens) {
        return badRequest()
                .json(error(
                        INVALID_REQUEST_ERROR,
                        """
                        prompt is too long:
                        %d tokens > %d maximum
                        """.formatted(estimatedTokens, maxTokens).trim()
                ))
                .build();
    }

    private String error(String type, String message) {
        return ClaudeErrorResponse.builder()
                .type("error")
                .error(
                        ClaudeError.builder()
                                .type(type)
                                .message(message)
                )
                .requestId("membrane_" + UUID.randomUUID())
                .toJson();
    }
}