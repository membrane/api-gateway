package com.predic8.membrane.core.transport.ws;

/**
 * Created by Predic8 on 12.04.2017.
 */
public interface WebSocketSender {
    public void handleFrame(WebSocketFrame frame) throws Exception;

}
