/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static org.junit.jupiter.api.Assertions.*;

class CorsInterceptorTest {

    CorsInterceptor i;

    @BeforeEach
    void setup() {
        i = new CorsInterceptor();
    }

    @Nested
    class HelperMethods {

        @Test
        void parseOriginSpaces() {
            i.setOrigins("foo bar baz");
            assertEquals(List.of("foo", "bar", "baz"), i.getAllowedOrigins());
        }

        @Test
        void parseMethodSpaces() {
            i.setMethods("GET, POST");
            assertEquals(List.of("GET", "POST"), i.getMethods());
        }
    }

    @Nested
    class Simple {

        @Test
        void getRequest() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com");

            Exchange exc = get("/test")
                    .header(ORIGIN, "https://trusted.example.com")
                    .buildExchange();
            exc.setResponse(ok("Hello").build());

            assertEquals(CONTINUE, i.handleRequest(exc));
            assertEquals(CONTINUE, i.handleResponse(exc));

            Header header = exc.getResponse().getHeader();
            assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertNull(header.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void requestFromUnauthorizedOriginGetsNoCorsHeaders() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("GET, POST");

            Exchange exc = get("/test")
                    .header(ORIGIN, "https://evil.example.com")
                    .buildExchange();
            exc.setResponse(ok("Nope").build());

            assertEquals(CONTINUE, i.handleRequest(exc));
            assertEquals(CONTINUE, i.handleResponse(exc));

            assertNull(exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void nonOptionsRequestWithoutOriginIsPassedThrough() throws URISyntaxException {
            Exchange exc = get("/public").buildExchange();
            exc.setResponse(ok("OK").build());

            assertEquals(CONTINUE, i.handleRequest(exc));
            assertEquals(CONTINUE, i.handleResponse(exc));
            assertNull(exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        }

    }

    @Nested
    class Preflight {

        @Test
        void explicitlyAllowedNullOrigin() throws URISyntaxException {
            i.setOrigins("foo bar null");

            Exchange exc = options("/test")
                    .header(ORIGIN, "null")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(204, exc.getResponse().getStatusCode());
        }

        @Test
        void originNullNotAllowed() throws URISyntaxException {
            i.setOrigins("foo bar");

            Exchange exc = options("/test")
                    .header(ORIGIN, "null")
                    .buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void restrictToRequestedMethod() throws URISyntaxException {
            Exchange exc = options("/test")
                    .header(ORIGIN, "https://trusted.example.com")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            Header header = exc.getResponse().getHeader();
            assertEquals(204, exc.getResponse().getStatusCode());
            assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertEquals("POST", header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS));
        }

        @Test
        void preflightRequestAllowedOrigin() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com");

            Exchange exc = options("/test")
                    .header(ORIGIN, "https://trusted.example.com")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            Header header = exc.getResponse().getHeader();

            assertEquals(204, exc.getResponse().getStatusCode());
            assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void preflightRequestWithoutOriginGetsIgnored() throws URISyntaxException {
            Exchange exc = options("/test").buildExchange();

            assertEquals(CONTINUE, i.handleRequest(exc));
            assertNull(exc.getResponse());
        }

        @Test
        void wildcardOriginWithoutCredentialsSetsAsterisk() throws URISyntaxException {
            i.setOrigins("*");
            i.setMethods("GET, POST");

            Exchange exc = options("/test")
                    .header(ORIGIN, "https://any.example.com")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals("https://any.example.com", exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertNull(exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void wildcardOriginWithCredentialsThrowsException() {
            assertThrows(ConfigurationException.class, () -> {
                i.setOrigins("*");
                i.setMethods("GET, POST");
                i.setCredentials(true);
                Exchange exc = options("/test")
                        .header(ORIGIN, "https://my.site")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .buildExchange();
                i.handleRequest(exc);
            });
        }

        @Test
        void shouldReturnOnlyRequestedMethodInCorsResponse() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("GET, POST");

            Exchange exc = options("/test")
                    .header(ORIGIN, "https://trusted.example.com")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .buildExchange();

            i.handleRequest(exc);

            assertEquals("POST", exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_METHODS));
        }

        @Test
        void shouldAllowNullOriginWhenExplicitlyConfigured() throws URISyntaxException {
            i.setOrigins("http://localhost:5173/ null");
            i.setMethods("GET, POST");

            Exchange exc = options("/test")
                    .header(ORIGIN, "null")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .buildExchange();
            i.handleRequest(exc);

            assertEquals(204, exc.getResponse().getStatusCode());
        }

        @Test
        void disallowedHeader() throws Exception {
            i.setOrigins("https://trusted.example.com");

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST,"X-Foo").buildExchange();

            i.handleRequest(exc);

            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void allowedHeader() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setHeaders("X-Bar X-Foo X-Zoo");

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST,"X-Foo").buildExchange();

            i.handleRequest(exc);

            assertEquals(204, exc.getResponse().getStatusCode());
        }


        @Test
        void shouldRejectRequestWithDisallowedMethod() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("GET");

            Exchange exc = options("/test")
                    .header(ORIGIN, "https://trusted.example.com")
                    .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                    .buildExchange();

            i.handleRequest(exc);

            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void preflightResponseContainsMaxAge() throws URISyntaxException {
            i.setOrigins("https://client.example.com");
            i.setMethods("POST, GET");
            i.setMaxAge("600");

            Exchange exc = options("/test")
                    .header(ORIGIN, "https://client.example.com")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals("600", i.getMaxAge());
            assertEquals("600", exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_MAX_AGE));
        }

        @Test
        void allowAllSetTrue() throws URISyntaxException {
            i.setAllowAll(true);

            Exchange exc = options("/test")
                    .header(ORIGIN, "https://any.example.com")
                    .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                    .buildExchange();

            i.handleRequest(exc);
            assertTrue(i.isAllowAll());
            assertEquals(204, exc.getResponse().getStatusCode());
        }

        @Test
        void allowAll() throws URISyntaxException {
            i.setAllowAll(true);
            i.setHeaders("X-Foo");

            Exchange exc = options("/test")
                    .header(ORIGIN, "https://any.example.com")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .buildExchange();

            i.handleRequest(exc);
            assertTrue(i.isAllowAll());
            assertEquals(204, exc.getResponse().getStatusCode());
        }
    }

    // TODO
    Builder createPreflight(String origin, String method) throws Exception {
        return createPreflight(origin, method, null);
    }

    Builder createPreflight(String origin, String method, String headers) throws Exception {
        Builder exc = options("/test")
                .header(ORIGIN, origin)
                .header(ACCESS_CONTROL_REQUEST_METHOD, method);

        if (headers != null)
            exc.header(ACCESS_CONTROL_REQUEST_HEADERS, headers);
        return exc;
    }
}
