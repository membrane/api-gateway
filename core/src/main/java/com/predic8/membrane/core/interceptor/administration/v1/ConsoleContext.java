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
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.router.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;

/**
 * Configuration of the admin API, fixed after {@code init()}.
 */
record ConsoleContext(Router router, String instanceName, boolean readOnly, boolean useXForwardedForAsClientAddr,
                      long startedAt) {

    /**
     * Fails with 404 unless {@code instance} names this instance. Requests for other instances are answered by the
     * aggregating console, not here.
     */
    void requireSelf(String instance) {
        if (!instanceName.equals(instance))
            throw notFound("Unknown instance.");
    }
}
