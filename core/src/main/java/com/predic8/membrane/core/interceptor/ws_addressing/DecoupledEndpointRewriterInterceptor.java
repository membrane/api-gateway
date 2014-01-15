/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.ws_addressing;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.io.StringWriter;
import java.io.Writer;

public class DecoupledEndpointRewriterInterceptor extends AbstractInterceptor {
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        System.out.println("DecoupledEndpointRewriterInterceptor.handleRequest()");
        System.out.println(getRegistry());

        Writer writer = new StringWriter();

        System.out.println("Body: " + exc.getRequest().getBodyAsStringDecoded());

        new DecoupledEndpointRewriter(getRegistry()).rewriteToElement(exc.getRequest().getBodyAsStream(), writer, exc);
        System.out.println(writer.toString());

        exc.getRequest().setBodyContent(writer.toString().getBytes());

        return Outcome.CONTINUE;
    }

    private DecoupledEndpointRegistry getRegistry() {
        return getRouter().getBeanFactory().getBean(DecoupledEndpointRegistry.class);
    }
}