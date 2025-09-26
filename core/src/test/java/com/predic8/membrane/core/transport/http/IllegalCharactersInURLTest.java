/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.*;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static org.junit.jupiter.api.Assertions.*;

class IllegalCharactersInURLTest {

    private HttpRouter r;

    @BeforeEach
    void init() throws Exception {
        r = new HttpRouter();
        r.setHotDeploy(false);
        r.add(new ServiceProxy(new ServiceProxyKey(3027), "localhost", 3028));
        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey(3028), null, 80);
        sp2.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                assertEquals("/foo{}", exc.getRequestURI());
                exc.setResponse(Response.ok().build());
                return Outcome.RETURN;
            }
        });
        r.add(sp2);
        r.start();
    }

    @AfterEach
    void unInit() {
        r.shutdown();
    }

    @Test
    void apacheHttpClient() {
        assertThrows(IllegalArgumentException.class, () -> {
            try (CloseableHttpClient hc = HttpClientBuilder.create().build()) {
                HttpResponse res = hc.execute(new HttpGet("http://localhost:3027/foo{}"));
                assertEquals(200, res.getStatusLine().getStatusCode());
            }
        });
    }

    @Test
    void illegal_with_router_tolerant_urifactory() throws Exception {
        r.setUriFactory(new URIFactory(true));
        makeCallWithIllegalCharacters(200);
    }

    @Test
    void illegal_with_router_intolerant_urifactory() throws Exception {
        r.setUriFactory(new URIFactory(false));
        makeCallWithIllegalCharacters(400);
    }

    private static void makeCallWithIllegalCharacters(int expectedStatusCode) throws Exception {
        try (HttpClient client = new HttpClient()) {
            assertEquals(expectedStatusCode, client.call(buildExchange())
                    .getResponse().getStatusCode());
        }
    }

    private static Exchange buildExchange() throws URISyntaxException {
        return new Request.Builder().get(new URIFactory(true),
                "http://localhost:3027/foo{}").buildExchange();
    }
}