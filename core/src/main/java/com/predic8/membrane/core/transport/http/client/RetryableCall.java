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

@FunctionalInterface
public interface RetryableCall {

    /**
     * Executes the operation for a given retry attempt.
     *
     * @param attempt the current attempt count, starting at 0
     * @return true if successful and no retry is needed
     * @throws Exception if the call fails (retryable or not)
     */
    boolean execute(Exchange exc, String dest, int attempt) throws Exception;
}

