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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;

class HTTPClientInterceptorTest {

    HTTPClientInterceptor hci;

    @BeforeEach
    void setUp() {
        hci = new HTTPClientInterceptor();
    }

    @Test
    void protocolUpgradeRejected() throws URISyntaxException {
        Router r = new Router();

        hci.init(r);

        Exchange e = get("http://localhost:2000/")
                .header(CONNECTION, "upgrade")
                .header(UPGRADE, "rejected")
                .buildExchange();
        e.setProxy(new NullProxy());

        hci.handleRequest(e);

        assertEquals(401, e.getResponse().getStatusCode());
    }

    @Test
    void passFailOverOn500Default() {
        hci.init(new Router());
        assertFalse(hci.getHttpClientConfig().getRetryHandler().isFailOverOn5XX());
    }

    @Test
    void passFailOverOn500() {
        hci.setFailOverOn5XX(true);
        hci.init(new Router());
        assertTrue(hci.getHttpClientConfig().getRetryHandler().isFailOverOn5XX());
    }

}