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

import com.predic8.membrane.core.http.Response;

import java.util.Collection;

public interface LLMErrorCreator {

    Response invalidRequestError(String message);

    Response tokenLimitExceeded(long tokenRequired, long tokenRemaining, long tokenResetInSeconds);

    Response modelNotAllowed(String model, Collection<String> allowedModels);

    Response authenticationFailed();

    /**
     *
     * @param maxTokens as configured
     * @param estimatedTokens estimated number of input tokens
     * @return Response error response
     */
    Response inputTokensExceeded(long maxTokens, long estimatedTokens);
}
