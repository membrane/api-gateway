package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * Created by Predic8 on 12.04.2017.
 */
public interface WebSocketInterceptorInterface {

    void init(Router router) throws Exception;

    void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception;

}
