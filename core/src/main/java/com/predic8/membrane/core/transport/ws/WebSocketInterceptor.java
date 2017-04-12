package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.interceptor.Outcome;

/**
 * Created by Predic8 on 12.04.2017.
 */
public interface WebSocketInterceptor {

    void handleFrame(WebSocketFrame frame, boolean frameTravelsToLeft, WebSocketSender sender) throws Exception;

}
