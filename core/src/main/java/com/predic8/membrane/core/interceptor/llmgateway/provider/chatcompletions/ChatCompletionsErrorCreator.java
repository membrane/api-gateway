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

package com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions;

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMErrorCreator;

import java.util.Collection;

import static com.predic8.membrane.core.http.Header.WWW_AUTHENTICATE;
import static com.predic8.membrane.core.http.Response.*;

public class ChatCompletionsErrorCreator extends AbstractLLMErrorCreator {

    @Override
    public Response invalidRequestError(String message) {
        return badRequest().json(envelope(message, "invalid_request_error", null, "bad_request")).build();
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

    public Response inputTokensExceeded(long maxTokens, long estimatedTokens) {
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
