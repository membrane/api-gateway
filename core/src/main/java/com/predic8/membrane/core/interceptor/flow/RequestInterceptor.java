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
import org.slf4j.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Interceptors are usually applied to requests and responses. By nesting interceptors into a
 * &lt;request&gt; Element you can limit their application to requests only.
 */
@MCElement(name = "request", topLevel = false)
public class RequestInterceptor extends AbstractFlowInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestInterceptor.class);

    public RequestInterceptor() {
        name = "Request Interceptor";
        setFlow(REQUEST);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        for (Interceptor i : getInterceptors()) {
            if (!i.handlesRequests())
                continue;
            log.debug("Invoking handler: {} on exchange: {}", i.getDisplayName(), exc);
            Outcome o = i.handleRequest(exc);

            // Get out on RETURN and ABORT
            if (o != CONTINUE)
                return o;
        }
        return CONTINUE;
    }
}
