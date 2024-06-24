package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;

@MCElement(name = "setHeader")
public class SetHeaderInterceptor extends AbstractSetterInterceptor {

    @Override
    protected boolean shouldSetValue(Exchange exchange, Message msg) {
        if (ifAbsent) {
            return !msg.getHeader().contains(name);
        }
        return true;
    }

    @Override
    protected void setValue(Exchange ignored, Message msg, String eval) {
        msg.getHeader().setValue(name, eval);
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
