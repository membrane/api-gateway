package com.predic8.application;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.tunnel.WebSocketInterceptor;
import com.predic8.membrane.core.interceptor.websocket.custom.MyWebSocketLogInterceptor;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class MyApplication {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting up");

        // create a new service proxy that listens on port 8080 and has a target to localhost:2001
        ServiceProxy sp = createServiceProxy();

        // create an enclosing WebSocket interceptor to add our own Logging interceptor to it
        WebSocketInterceptor ws = new WebSocketInterceptor();
        ws.getInterceptors().add(new MyWebSocketLogInterceptor());

        // attach the WebSocket interceptor to the service proxy
        sp.getInterceptors().add(ws);

        // add the service proxy to a new router instance and start it
        HttpRouter router = new HttpRouter();
        router.add(sp);
        router.init();

        System.out.println("Starting finished - Waiting for WebSocket communication");
    }

    private static ServiceProxy createServiceProxy() {
        String hostname = "*";
        String method = "*";
        String path = ".*";
        int listenPort = 8080;

        ServiceProxyKey key = new ServiceProxyKey(hostname, method, path, listenPort);

        String targetHost = "localhost";
        int targetPort = 2001;

        return new ServiceProxy(key, targetHost, targetPort);
    }
}
