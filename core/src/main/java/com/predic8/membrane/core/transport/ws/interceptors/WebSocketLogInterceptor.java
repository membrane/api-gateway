package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;

@MCElement(name = "wsLog")
public class WebSocketLogInterceptor implements WebSocketInterceptorInterface {
    @Override
    public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
        System.out.println("Frame travels to " + (frameTravelsToRight ? "right" : "left"));
        System.out.println(frame.toString());
        sender.handleFrame(frame);
    }
}
