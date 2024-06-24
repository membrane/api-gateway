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
