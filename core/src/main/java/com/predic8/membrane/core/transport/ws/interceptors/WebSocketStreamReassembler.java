package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.InterceptorFlowController;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;

import java.util.ArrayList;
import java.util.List;

@MCElement(name = "streamReassembler")
public class WebSocketStreamReassembler implements WebSocketInterceptorInterface {

    List<Interceptor> interceptors = new ArrayList<>();

    @Override
    public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
        if (frameTravelsToRight) {
            //TODO: create exchange from frame(s)
            Exchange exc = null;
            new InterceptorFlowController().invokeHandlers(exc, interceptors);
            //...
        }
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @MCChildElement
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }
}
