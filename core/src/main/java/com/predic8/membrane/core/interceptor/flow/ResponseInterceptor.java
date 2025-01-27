/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Interceptors are usually applied to requests and responses. By nesting interceptors into a
 * &lt;response&gt; plugin you can limit their application to responses only.
 * @topic 1. Proxies and Flow
 */
@MCElement(name = "response", topLevel = false)
public class ResponseInterceptor extends AbstractFlowInterceptor {

    @Override
    public Outcome handleResponse(Exchange exc) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            Interceptor interceptor = interceptors.get(i);
            if (interceptor.handlesResponses()) {
                try {
                    if (interceptor.handleResponse(exc) == ABORT)
                        return ABORT;
                } catch (Exception e) {
                    createProblemDetails( "response", interceptor, exc, e);
                    return ABORT;
                }
            }
        }
        return CONTINUE;
    }
}
