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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;

import java.util.Optional;

@MCElement(name = "setHeader")
public class SetHeaderInterceptor extends AbstractSetterInterceptor {

    @Override
    protected boolean shouldSetValue(Exchange exc, Flow flow) {
        if (ifAbsent) {
            var msg = msgFromExchange(exc, flow);
            return msg.filter(message -> !message.getHeader().contains(name)).isPresent();
        }
        return true;
    }

    @Override
    protected void setValue(Exchange exc, Flow flow, String eval) {
        var msg = msgFromExchange(exc, flow);
        msg.ifPresent(message -> message.getHeader().setValue(name, eval));
    }

    private Optional<Message> msgFromExchange(Exchange exc, Flow flow) {
        return switch (flow) {
            case REQUEST -> Optional.of(exc.getRequest());
            case RESPONSE -> Optional.of(exc.getResponse());
            case ABORT -> Optional.empty();
        };
    }

    @Override
    public String getDisplayName() {
        return "setHeader";
    }

    @Override
    public String getShortDescription() {
        return String.format("Sets the value of the HTTP header '%s' to %s.", name, value);
    }
}
