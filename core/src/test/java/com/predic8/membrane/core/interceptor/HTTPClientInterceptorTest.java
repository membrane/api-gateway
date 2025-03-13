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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.proxies.NullProxy;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class HTTPClientInterceptorTest {

    @Test
    public void testProtocolUpgradeRejected() throws URISyntaxException {
        Router r = new Router();

        HTTPClientInterceptor hci = new HTTPClientInterceptor();
        hci.init(r);

        Exchange e = Request.get("http://localhost:2000/")
                .header("Connection", "upgrade")
                .header("Upgrade", "rejected")
                .buildExchange();
        e.setProxy(new NullProxy());

        hci.handleRequest(e);

        assertEquals(401, e.getResponse().getStatusCode());
    }

}