/*
 *  Copyright 2017 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

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
