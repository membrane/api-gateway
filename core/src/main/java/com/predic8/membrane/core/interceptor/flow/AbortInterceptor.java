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
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;

/**
 * @description Plugins are usually applied to requests and responses.
 * In case of errors, the flow returns and <i>handleAbort()</i> is called on plugins
 * going back the chain.
 * By nesting plugins into an &lt;abort&gt; you can limit their application to abort flows only.
 * On plugins nested in &lt;abort&gt; handleResponse() is called not handleAbort() in order to
 * allow normal processing.
 */
@MCElement(name="abort", topLevel=false)
public class AbortInterceptor extends AbstractFlowInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbortInterceptor.class);

    public AbortInterceptor() {
        name = "Abort";
        setFlow(RESPONSE_ABORT_FLOW);
    }

    @Override
    public void handleAbort(Exchange exchange) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            Interceptor interceptor = interceptors.get(i);
            if (interceptor.handlesResponses()) {
                log.debug("Invoking handleResponse() on {}", interceptor);
                try {
                    interceptor.handleResponse(exchange);
                } catch (Exception e) {
                    log.error("{} interceptor caused an error in abort plugin. Message: {} \n{}",interceptor, e.getMessage(),e);
                    ProblemDetails.internal(router.isProduction())
                            .detail("Error in abort plugin")
                            .component("abort")
                            .exception(e)
                            .buildAndSetResponse(exchange);
                }
            }
        }
    }
}