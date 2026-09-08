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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The error body Anthropic returns, so that a client talking to the gateway can parse a rejection the
 * same way it parses one from the provider.
 *
 * <pre><code>
 * { "type": "error", "error": { "type": "invalid_request_error", "message": "..." }, "request_id": "..." }
 * </code></pre>
 */
public record ClaudeErrorResponse(String type, ClaudeError error, @JsonProperty("request_id") String requestId) {

    public static ClaudeErrorResponse of(String errorType, String message, String requestId) {
        return new ClaudeErrorResponse("error", new ClaudeError(errorType, message), requestId);
    }

    public record ClaudeError(String type, String message) {
    }
}
