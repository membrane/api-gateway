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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;

public abstract class AbstractFlowInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractFlowInterceptor.class);

    protected List<Interceptor> interceptors = new ArrayList<>();

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Override
    public void init() {
        super.init();
        for (Interceptor i : interceptors)
            i.init(router);
    }

    protected static void createProblemDetails(String flow, Interceptor interceptor, Exchange exc, Exception e) {
        String msg = "Aborting! Exception caused by %s %s during %s flow.".formatted(exc.getRequest().getUri(), flow, interceptor.getDisplayName()); // Flow is capital to make it the same as in other places
        log.warn(msg, e);
        internal(false, "flow-interceptor")
                .detail(msg)
                .component(interceptor.getDisplayName())
                .exception(e)
                .buildAndSetResponse(exc);
    }
}
