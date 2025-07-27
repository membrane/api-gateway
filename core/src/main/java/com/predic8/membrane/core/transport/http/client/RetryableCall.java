/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.core.exchange.*;

/**
 * Functional interface for operations that support retry logic.
 * Used internally by the HTTP client to wrap logic that may be retried (e.g. outbound HTTP requests).
 * Implementations return {@code true} if the operation succeeded and no retry is needed.
 * Returning {@code false} or throwing an exception indicates failure, which may trigger a retry
 * depending on the configured {@code maxRetries}.
 *
 * This interface is typically used by {@link RetryHandler}.
 *
 */
@FunctionalInterface
public interface RetryableCall {

    /**
     * Executes the operation for a given retry attempt.
     *
     * @param attempt the current attempt count, starting at 0
     * @return true if successful and no retry is needed
     * @throws Exception if the call fails (retryable or not)
     */

    /**
     * Executes the actual retryable operation (e.g., an HTTP call).
     * This method is called once per attempt.
     *
     * @param exc the current exchange object
     * @param dest the destination URI or identifier of the target
     * @param attempt the current retry attempt (starting at 0)
     * @return true if the operation succeeded and no further retries are necessary
     * @throws Exception if the operation fails; may be retried depending on retry policy
     */
    boolean execute(Exchange exc, String dest, int attempt) throws Exception;
}

