/* Copyright 2024 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.ABORT;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Interceptors are usually applied to requests and responses. In case of errors, interceptors can initiate the abort flow to safely shut down Membrane.
 *              By nesting interceptors into an &lt;abort&gt; Element you can limit their application to abort flows only.
 */
@MCElement(name="abort", topLevel=false)
public class AbortInterceptor extends AbstractFlowInterceptor {

    /**
     * (Yes, this needs to be handled in handleREQUEST.)
     */
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        for (Interceptor i : getInterceptors()) {
            if (i.getFlow().contains(ABORT))
                exc.pushInterceptorToStack(i);
        }
        return CONTINUE;
    }

}
