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

package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.core.interceptor.flow.invocation.AbstractInterceptorFlowTest;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.A;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rejection must put the exchange into the abort flow, so that the interceptors before
 * jsonRPCProtection see handleAbort instead of handleResponse. See
 * {@link com.predic8.membrane.core.interceptor.flow.invocation.InterceptorFlowTest} for the
 * notation: {@code >a} request flow, {@code <a} response flow, {@code ?a} abort flow.
 */
class JsonRPCProtectionAbortFlowTest extends AbstractInterceptorFlowTest {

    @Test
    void rejectedRequestAbortsTheFlow() throws Exception {
        // The harness POSTs form-urlencoded, which jsonRPCProtection rejects as not JSON.
        // assertFlow() cannot be used because the body is the JSON-RPC error envelope the plugin set.
        String response = getResponse(A, new JsonRPCProtectionInterceptor());

        assertTrue(response.endsWith("?a"), response);
        assertFalse(response.contains("<a"), response);
    }
}
