/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.discovery;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;

class DiscoveryTestSupport {

    static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,*/*;q=0.8";

    private static final ObjectMapper om = new ObjectMapper();

    static DummyTestRouter routerWithShop() throws Exception {
        final var router = new DummyTestRouter();
        router.add(shop());
        router.init();
        return router;
    }

    static ServiceProxy shop() {
        final var shop = new ServiceProxy(new ServiceProxyKey("*", "*", "/shop", 2000), null, 0);
        shop.setName("Shop");
        return shop;
    }

    static ServiceProxy proxyAt(String path) {
        return new ServiceProxy(new ServiceProxyKey("*", "*", path, 2002), null, 0);
    }

    static <T extends AbstractApiDiscoveryInterceptor> T initialized(T interceptor, Router router) {
        interceptor.init(router);
        return interceptor;
    }

    static Exchange request(String method) throws Exception {
        final var exc = new Request.Builder().method(method).uri("/apis.json")
                .header("Host", "gateway.example.com:2000").buildExchange();
        exc.setOriginalHostHeader("gateway.example.com:2000");
        return exc;
    }

    static Exchange browserRequest() throws Exception {
        final var exc = request("GET");
        exc.getRequest().getHeader().setValue(Header.ACCEPT, BROWSER_ACCEPT);
        return exc;
    }

    static JsonNode body(Exchange exc) throws Exception {
        return om.readTree(exc.getResponse().getBodyAsStringDecoded());
    }

    static String header(Exchange exc, String name) {
        return exc.getResponse().getHeader().getFirstValue(name);
    }
}
