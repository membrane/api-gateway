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

package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;

public abstract class AbstractLLMErrorCreator implements LLMErrorCreator {

    private static final ObjectMapper om = new ObjectMapper();

    public static String createJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return """
                    { "error": "Could not create JSON" }
                    """;
        }
    }

    public String envelope(String message, String type, String param, String code) {
        return createJson(new ErrorEnvelope(new ErrorBody(message,type,param,code)));
    }

    /**
     * Every provider rejects a model the same way, only the envelope around it differs.
     */
    protected static String modelNotAllowedMessage(String model, Collection<String> allowedModels) {
        return "Model '%s' is not allowed. Allowed models: %s.".formatted(model, String.join(", ", allowedModels));
    }

    /**
     * What is left of the budget is reported as zero once it is used up: the client has no use for
     * the overdraft.
     */
    protected static String tokenLimitMessage(long tokenRequired, long tokenRemaining, long tokenResetInSeconds) {
        return """
                Token rate limit exceeded.
                Request requires %d tokens but only %d remain.
                Retry after %d seconds.
                """.formatted(tokenRequired, Math.max(0, tokenRemaining), tokenResetInSeconds).trim();
    }

    private record ErrorEnvelope(ErrorBody error) {
    }

    private record ErrorBody(
            String message,
            String type,
            String param,
            String code
    ) {
    }
}
