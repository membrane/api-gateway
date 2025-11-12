package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowWithChildrenInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MCElement(name = "time")
public class TimingInterceptor extends AbstractFlowWithChildrenInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TimingInterceptor.class);

    public Outcome handleInternal(Exchange exc, Flow flow) {
        return switch (flow) {
            case REQUEST -> getFlowController().invokeRequestHandlers(exc, getFlow());
            case RESPONSE -> getFlowController().invokeResponseHandlers(exc, getFlow());
            default -> throw new RuntimeException("Should never happen");
        };
    }

}
