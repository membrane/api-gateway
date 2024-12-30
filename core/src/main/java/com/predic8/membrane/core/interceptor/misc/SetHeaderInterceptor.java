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
package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;

@MCElement(name = "setHeader")
public class SetHeaderInterceptor extends AbstractSetterInterceptor {

    @Override
    protected boolean shouldSetValue(Exchange exc, Flow flow) {
        if (ifAbsent) {
            return !msgFromExchange(exc, flow).getHeader().contains(name);
        }
        return true;
    }

    @Override
    protected void setValue(Exchange exc, Flow flow, String eval) {
        msgFromExchange(exc, flow).getHeader().setValue(name, eval);
    }

    /**
     * TOOO this is duplicated and can be found somewhere else
     * @param exc
     * @param flow
     * @return
     */
    private Message msgFromExchange(Exchange exc, Flow flow) {
        return switch (flow) {
            case REQUEST -> exc.getRequest();
            case RESPONSE, ABORT -> exc.getResponse();
        };
    }

    @Override
    public String getDisplayName() {
        return "setHeader";
    }

    @Override
    public String getShortDescription() {
        return "Sets the value of the HTTP header '%s' to the expression: %s.".formatted( name, value);
    }
}
