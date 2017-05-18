package com.predic8.membrane.core.interceptor.websocket.custom;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;

public class MyWebSocketLogInterceptor implements WebSocketInterceptorInterface {

    public void init(Router router) throws Exception {
    }

    public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
        System.out.println("Frame travels from " + (frameTravelsToRight ? "client to server" : "server to client"));
        System.out.println(frame.toString());
        sender.handleFrame(frame);
    }
}

