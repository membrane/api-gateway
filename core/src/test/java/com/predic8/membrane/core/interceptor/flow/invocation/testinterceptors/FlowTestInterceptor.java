/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.flow.invocation.testinterceptors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

public class FlowTestInterceptor extends AbstractInterceptor {

    private final String name;

    public FlowTestInterceptor(String name) {
        this.name = name;
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        addStringToBody(exc.getRequest(),">" + name);
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        addStringToBody(createResponseIfThereIsNone(exc),"<" + name);
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exc) {
        addStringToBody(createResponseIfThereIsNone(exc), "?" + name);
        exc.setProperty("status", "aborted");
    }

    private static Response createResponseIfThereIsNone(Exchange exc) {
        if (exc.getResponse() != null) {
            return exc.getResponse();
        }
        Response msg = Response.ok().body(exc.getRequest().getBodyAsStringDecoded()).build();
        exc.setResponse(msg);
        return msg;
    }

    private void addStringToBody(Message msg, String s) {
        msg.setBodyContent((msg.getBodyAsStringDecoded() + s).getBytes());
    }

    @Override
    public String toString() {
        return name;
    }
}
