/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.api;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

import java.io.IOException;

/**
 * A basic example on how to embed Membrane service proxy into Java programs.
 *
 * In this example the router will forward any incoming HTTP GET requests on port 4000
 * to http://predic8.com. Furthermore a custom interceptor will be added which adds
 * the header 'Hello-X' to the HTTP request.
 *
 * To execute the example, proceed with the following steps:
 * - Run the main() method
 * - Open up a browser
 * - Enter the url http://localhost:4000; you should be forwarded to predic8.com
 *
 * @author Oliver Weiler
 */
public class EmbeddingJava {
    public static void main(String[] args) throws IOException {
        String hostname = "*";
        String method = "GET";
        String path = ".*";
        int listenPort = 4000;

        ServiceProxyKey key = new ServiceProxyKey(hostname, method, path, listenPort);

        String targetHost = "predic8.com";
        int targetPort = 80;

        Rule serviceProxy = new ServiceProxy(key, targetHost, targetPort);

        HttpRouter router = new HttpRouter();

        serviceProxy.getInterceptors().add(new AddMyHeaderInterceptor());
        router.getRuleManager().addProxyAndOpenPortIfNew(serviceProxy);
    }
}