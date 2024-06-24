package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;

@MCElement(name = "setProperty")
public class SetPropertyInterceptor extends AbstractSetterInterceptor {

    @Override
    protected boolean shouldSetValue(Exchange exchange, Message msg) {
        if (ifAbsent) {
            return exchange.getProperty(name) == null;
        }
        return true;
    }

    @Override
    protected void setValue(Exchange exchange, Message ignored, String eval) {
        exchange.setProperty(name, eval);
    }

    @Override
    public String getDisplayName() {
        return "setProperty";
    }

    @Override
    public String getShortDescription() {
        return String.format("Sets the value of the exchange property '%s' to %s.", name, value);
    }
}
