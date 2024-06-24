package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;

@MCElement(name = "setHeader")
public class SetHeaderInterceptor extends AbstractSetterInterceptor {

    @Override
    protected boolean shouldSetValue(Exchange exc, Flow flow) {
        if (ifAbsent) {
            return !msgFromExchange(exc).getHeader().contains(name);
        }
        return true;
    }

    @Override
    protected void setValue(Exchange exc, Flow flow, String eval) {
        msgFromExchange(exc).getHeader().setValue(name, eval);
    }

    private Message msgFromExchange(Exchange exc) {
        return exc.getRequest() != null ? exc.getRequest() : exc.getResponse();
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
