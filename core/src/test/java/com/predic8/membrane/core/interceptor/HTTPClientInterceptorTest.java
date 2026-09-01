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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.text.*;
import com.predic8.membrane.core.util.text.SerializationUtil.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static com.predic8.membrane.core.util.text.SerializationUtil.Serialization.*;
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
        testExpression(GROOVY, exc, "http://localhost/foo/${header.foo}: {}${header.bar}", "http://localhost/foo/%25+%24%7B%7D: {}%24%26%3A%2F%29", Serialization.URL);
    }

    @Test
    void computeTargetUrlWithEncodingSpEL() throws Exception {
        var exc = get("/foo")
                .header("foo", "% ${}")
                .header("bar", "$&:/)")
                .buildExchange();
        testExpression(SPEL, exc, "http://localhost/foo/${header.foo}: {}${header.bar}", "http://localhost/foo/%25+%24%7B%7D: {}%24%26%3A%2F%29", Serialization.URL);
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
        testExpression(JSONPATH, exc, "http://localhost/foo/${$.foo}: {}${$.bar}", "http://localhost/foo/%25+%24%7B%7D: {}%24%26%3A%2F%29", Serialization.URL);
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
                "http://localhost/foo/%25+%24%7B%7D: {}%24%26%3A%2F%29", Serialization.URL);
    }

    @Test
    void computeTextEscaping() throws Exception {
        var exc = post("/foo").buildExchange();
        testExpression(SPEL, exc, "http://localhost/foo/${'&?äöü!\"=:#/\\'}",
                "http://localhost/foo/&?äöü!\"=:#/\\", TEXT);
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
        testExpression(SPEL, exc, "${header['X-URL']}", completePath, TEXT);
    }

    @Test
    void computeCompletePathURLEncoded() throws Exception {
        var exc = post("/foo").buildExchange();
        testExpression(SPEL, exc, "${'&?äöü!'}",
                "%26%3F%C3%A4%C3%B6%C3%BC%21", Serialization.URL);
    }

    /**
     * A refused target, a target that never accepts and one that accepts but stays silent used to be
     * logged and reported alike. They have to stay distinguishable by status, subSee and detail.
     */
    @Nested
    class unreachableTarget {

        @Test
        void refusedTargetYields502() throws Exception {
            int freePort;
            try (var probe = new ServerSocket(0)) {
                freePort = probe.getLocalPort();
            } // closed again, so nothing listens on freePort

            var exc = callTarget("http://localhost:" + freePort + "/", 0);

            assertEquals(502, exc.getResponse().getStatusCode());
            assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("connect"));
        }

        @Test
        void silentTargetYields504NamingTheReadPhase() throws Exception {
            try (var silent = new ServerSocket(0)) {
                // accepts the connection but never writes a response, so the client hits its read timeout
                var exc = callTarget("http://localhost:" + silent.getLocalPort() + "/", 250);

                assertEquals(504, exc.getResponse().getStatusCode());
                var body = exc.getResponse().getBodyAsStringDecoded();
                assertTrue(body.contains("socket-timeout"), body);
                assertTrue(body.contains("waiting for the response"), body);
            }
        }

        @Test
        void connectTimeoutYields504NamingTheConnectPhase() throws Exception {
            // A real dropped SYN is not reproducible here, so the client is made to report one
            var hci = new HTTPClientInterceptor(new HttpClient() {
                @Override
                public void call(Exchange exc) throws Exception {
                    throw new ConnectTimeoutException("Connecting to example.com:80 timed out after 10000ms.",
                            new SocketTimeoutException("Connect timed out"));
                }
            });
            hci.init(router);

            var exc = get("http://example.com/").buildExchange();
            exc.setProxy(new NullProxy());
            exc.getDestinations().add("http://example.com/");

            hci.handleRequest(exc);

            assertEquals(504, exc.getResponse().getStatusCode());
            var body = exc.getResponse().getBodyAsStringDecoded();
            assertTrue(body.contains("connect-timeout"), body);
            assertTrue(body.contains("no request was sent"), body);
        }

        private Exchange callTarget(String url, int soTimeout) throws Exception {
            var config = new HttpClientConfiguration();
            config.getConnection().setSoTimeout(soTimeout);
            // a read timeout must not be retried, so the assertions see the first failure
            config.getRetryHandler().setRetries(0);
            hci.setHttpClientConfig(config);
            hci.init(router);

            var exc = get(url).buildExchange();
            exc.setProxy(new NullProxy());
            exc.getDestinations().add(url);

            hci.handleRequest(exc);
            return exc;
        }
    }

    @Nested
    class injection {

        @Test
        void illegalCharactersAndTemplateInTargetURL() throws URISyntaxException {
            allowIllegalURICharacters();
            var exc = get("/foo").buildExchange();
            assertThrows(ConfigurationException.class, () -> invokeDispatching(SPEL, exc, "https://${'hostname'}", Serialization.URL));
        }

        @Test
        void illegalCharacterWithoutTemplate() {
            allowIllegalURICharacters();
            var exc = new Request.Builder().method(METHOD_GET).uri("/foo/${555}").buildExchange();
            invokeDispatching(SPEL, exc, "https://localhost", Serialization.URL);
            if (!(exc.getProxy() instanceof APIProxy apiProxy)) {
                fail();
                return;
            }
            assertFalse(apiProxy.getTarget().isUrlIsTemplate());
            assertEquals(1, exc.getDestinations().size());

            // The template should not be evaluated, cause illegal characters are allowed!
            assertEquals("https://localhost/foo/${555}", exc.getDestinations().getFirst());
        }
    }

    private void allowIllegalURICharacters() {
        router.getConfiguration().setUriFactory(new URIFactory(true));
    }

    private void testExpression(Language language, Exchange exc, String url, String expected, Serialization escaping) {
        invokeDispatching(language, exc, url, escaping);
        assertEquals(1, exc.getDestinations().size());
        assertEquals(expected, exc.getDestinations().getFirst());
    }

    private void invokeDispatching(Language language, Exchange exc, String url, Serialization escaping) {
        var target = new Target();
        target.setUrl(url);
        target.setLanguage(language);
        target.setEscaping(escaping);
        target.init(router);

        var api = new APIProxy();
        api.setTarget(target);
        exc.setProxy(api);
        hci.init(router);
        var di = new DispatchingInterceptor();
        di.init(router);
        di.handleRequest(exc);
        hci.applyTargetModifications(exc);
    }

}