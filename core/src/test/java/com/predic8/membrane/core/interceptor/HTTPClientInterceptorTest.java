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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.proxies.Target.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static com.predic8.membrane.core.proxies.Target.Escaping.*;
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
    void computeTargetUrlWithEncodingGroovy() throws Exception {
        var exc = get("/foo")
                .header("foo", "% ${}")
                .header("bar", "$&:/)")
                .buildExchange();
        testExpression(GROOVY, exc, "http://localhost/foo/${header.foo}: {}${header.bar}", "http://localhost/foo/%25+%24%7B%7D: {}%24%26%3A%2F%29", Escaping.URL);
    }

    @Test
    void computeTargetUrlWithEncodingSpEL() throws Exception {
        var exc = get("/foo")
                .header("foo", "% ${}")
                .header("bar", "$&:/)")
                .buildExchange();
        testExpression(SPEL, exc, "http://localhost/foo/${header.foo}: {}${header.bar}", "http://localhost/foo/%25+%24%7B%7D: {}%24%26%3A%2F%29", Escaping.URL);
    }

    @Test
    void computeTargetUrlWithEncodingJsonPath() throws Exception {
        var exc = post("/foo")
                .json("""
                        {
                          "foo": "% ${}",
                          "bar": "$&:/)"
                        }
                        """)
                .buildExchange();
        testExpression(JSONPATH, exc, "http://localhost/foo/${$.foo}: {}${$.bar}", "http://localhost/foo/%25+%24%7B%7D: {}%24%26%3A%2F%29", Escaping.URL);
    }

    @Test
    void computeTargetUrlWithEncodingXPath() throws Exception {
        var exc = post("/foo")
                .xml("""
                        <root>
                          <foo>% ${}</foo>
                          <bar>$&amp;:/)</bar>
                        </root>
                        """)
                .buildExchange();
        testExpression(XPATH, exc, "http://localhost/foo/${//foo}: {}${//bar}",
                "http://localhost/foo/%25+%24%7B%7D: {}%24%26%3A%2F%29", Escaping.URL);
    }

    @Test
    void computeNoneEscaping() throws Exception {
        var exc = post("/foo").buildExchange();
        testExpression(SPEL, exc, "http://localhost/foo/${'&?äöü!\"=:#/\\'}",
                "http://localhost/foo/&?äöü!\"=:#/\\", NONE);
    }

    @Test
    void computeSegmentEscaping() throws Exception {
        var exc = post("/foo").buildExchange();
        testExpression(SPEL, exc, "http://localhost/foo/${'&?äöü!\"=:#/\\'}",
                "http://localhost/foo/%26%3F%C3%A4%C3%B6%C3%BC%21%22%3D%3A%23%2F%5C", SEGMENT);
    }

    @Test
    void computeCompletePath() throws Exception {
        var completePath = "https://predic8.com/foo?bar=baz";
        var exc = post("/foo")
                .header("X-URL", completePath)
                .buildExchange();
        testExpression(SPEL, exc, "${header['X-URL']}", completePath, NONE);
    }

    @Test
    void computeCompletePathURLEncoded() throws Exception {
        var exc = post("/foo").buildExchange();
        testExpression(SPEL, exc, "${'&?äöü!'}",
                "%26%3F%C3%A4%C3%B6%C3%BC%21", Escaping.URL);
    }


    private void testExpression(Language language, Exchange exc, String url, String expected, Escaping escaping) {
        var target = new Target();
        target.setUrl(url);
        target.setLanguage(language);
        target.setEscaping(escaping);

        var api = new APIProxy();
        api.setTarget(target);
        exc.setProxy(api);
        hci.init(router);
        new DispatchingInterceptor().handleRequest(exc);
        hci.applyTargetModifications(exc);
        assertEquals(1, exc.getDestinations().size());
        assertEquals(expected, exc.getDestinations().getFirst());
    }

}