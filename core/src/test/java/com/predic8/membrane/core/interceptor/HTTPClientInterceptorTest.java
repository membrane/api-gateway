/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;

class HTTPClientInterceptorTest {

    HTTPClientInterceptor hci;
    Router router;

    @BeforeEach
    void setUp() {
        hci = new HTTPClientInterceptor();
        router = new DefaultRouter();
    }

    @Test
    void protocolUpgradeRejected() throws URISyntaxException {
        hci.init(router);

        var exc = get("http://localhost:2000/")
                .header(CONNECTION, "upgrade")
                .header(UPGRADE, "rejected")
                .buildExchange();
        exc.setProxy(new NullProxy());

        hci.handleRequest(exc);

        assertEquals(401, exc.getResponse().getStatusCode());
    }

    @Test
    void passFailOverOn500Default() {
        hci.init(router);
        assertFalse(hci.getHttpClientConfig().getRetryHandler().isFailOverOn5XX());
    }

    @Test
    void passFailOverOn500() {
        hci.setFailOverOn5XX(true);
        hci.init(router);
        assertTrue(hci.getHttpClientConfig().getRetryHandler().isFailOverOn5XX());
    }

    @Test
    void computeTargetUrl() throws Exception {
        var target = new Target();
        target.setUrl("http://localhost/foo/${urlEncode(header.foo)}");

        var api = new APIProxy();
        api.setTarget(target);

        var exc = get("/foo").header("foo","% ${}").buildExchange();
        exc.setProxy(api);
        hci.init(router);
        new DispatchingInterceptor().handleRequest(exc);
        hci.applyTargetModifications(exc);
        assertEquals(1, exc.getDestinations().size());
        assertEquals("http://localhost/foo/%25+%24%7B%7D", exc.getDestinations().getFirst());
    }

}