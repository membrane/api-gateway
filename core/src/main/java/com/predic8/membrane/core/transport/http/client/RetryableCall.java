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

import com.predic8.membrane.core.exchange.Exchange;

/**
 * One attempt of {@link RetryHandler#executeWithRetries(Exchange, RetryableCall)} - the call to the
 * backend that the handler repeats when it fails.
 * <p>
 * Returning {@code true} ends the sequence at once, which the HTTP client uses for an exchange that
 * turned into a tunnel and has no response to inspect. Returning {@code false} hands the response set
 * on the exchange back to the handler, which decides from its status code whether to try again. An
 * exception is retried or rethrown according to the retry policy.
 */
@FunctionalInterface
public interface RetryableCall {

    /**
     * Runs a single attempt.
     *
     * @param exc the current exchange; the implementation sets the response on it
     * @param dest the destination this attempt is sent to
     * @param attempt zero-based number of this attempt
     * @return true to end the sequence without inspecting a response
     * @throws Exception if the attempt fails; the retry policy decides whether it is repeated
     */
    boolean execute(Exchange exc, String dest, int attempt) throws Exception;
}

