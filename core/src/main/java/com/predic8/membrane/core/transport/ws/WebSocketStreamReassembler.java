package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.InterceptorFlowController;
import com.sun.javafx.scene.control.skin.VirtualFlow;

import java.util.ArrayList;
import java.util.List;

public class WebSocketStreamReassembler implements WebSocketInterceptor {

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

    @MCChildElement
    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }
}
