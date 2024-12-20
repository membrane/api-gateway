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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

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
//        for (Interceptor i : getInterceptors()) {
//            if (i.getFlow().contains(ABORT))
//                exc.pushInterceptorToStack(new AdapterInterceptor(i));
//        }
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exchange) {
        System.out.println("AbortInterceptor.handleAbort");
        for (Interceptor i : getInterceptors().reversed()) {
            System.out.println("In Abort for: " + i);
            System.out.println("Interceptor supports Flows " + i.getFlow());
            if ((i.getFlow().contains(Flow.RESPONSE))) {
                try {
                    i.handleResponse(exchange);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

    static class AdapterInterceptor extends AbstractInterceptor {

        Interceptor nested;

        public AdapterInterceptor(Interceptor nested) {
            this.nested = nested;
        }

        @Override
        public void handleAbort(Exchange exchange) {
            try {
                nested.handleResponse(exchange);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
