/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import org.apache.logging.log4j.ThreadContext;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name="logContext")
public class LoggingContextInterceptor extends AbstractInterceptor{
    private final String proxyName = "proxyName";

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        ThreadContext.put(proxyName, exc.getRule().getName());
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        ThreadContext.remove(proxyName);
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exchange) {
        ThreadContext.remove(proxyName);
    }
}
