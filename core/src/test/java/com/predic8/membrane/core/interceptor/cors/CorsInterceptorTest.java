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

        @Test
        void parseHeaderSpaces() {
            i.setHeaders("foo bar baz");
            assertEquals(List.of("foo", "bar", "baz"), i.getAllowedHeaders());
        }

        @Test
        void credentialsWithOriginWildcard() {
            i.setCredentials(true);
            assertThrows(ConfigurationException.class, () -> i.init());
        }
    }

    @Nested
    class Simple {

        @Test
        void setAllowHeadersOnResponse() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com");

            Exchange exc = get("/test")
                    .header(ORIGIN, "https://trusted.example.com")
                    .buildExchange();
            exc.setResponse(ok("Hello").build());

            assertEquals(CONTINUE, i.handleRequest(exc)); // Not an OPTIONS request just CONTINUE
            assertEquals(CONTINUE, i.handleResponse(exc));

            Header header = exc.getResponse().getHeader();
            assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertNull(header.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void nonOptionsRequestWithoutOriginIsPassedThrough() throws URISyntaxException {
            Exchange exc = get("/public").buildExchange();
            exc.setResponse(ok("OK").build());

            assertEquals(CONTINUE, i.handleRequest(exc));
            assertEquals(CONTINUE, i.handleResponse(exc));
            assertNull(getAllowOrigin(exc));
        }

        @Test
        void unauthorizedOriginAllowHeadersShouldNotBeAddedToResponse() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("GET, POST");

            Exchange exc = get("/test")
                    .header(ORIGIN, "https://evil.example.com")
                    .buildExchange();

            assertEquals(CONTINUE, i.handleRequest(exc));
            exc.setResponse(ok("Nope").build());
            assertEquals(CONTINUE, i.handleResponse(exc));

            assertNull(getAllowOrigin(exc)); // AllowOrigin header must not be set
        }

    }

    @Nested
    class Preflight {

        @Test
        void explicitlyAllowedNullOrigin() throws Exception {
            i.setOrigins("foo bar null");

            Exchange exc = createPreflight("null", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(204, exc.getResponse().getStatusCode());
        }

        @Test
        void originNullNotAllowed() throws Exception {
            Exchange exc = createPreflight("null", null).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void preflightReturnsOnlyRequestedMethod() throws Exception {
            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            Header header = exc.getResponse().getHeader();
            assertEquals(204, exc.getResponse().getStatusCode());
            assertEquals("https://trusted.example.com", getAllowOrigin(exc));
            assertEquals("POST", getAllowMethods(exc));
        }

        @Test
        void preflightRequestAllowedOrigin() throws Exception {
            i.setOrigins("https://trusted.example.com");

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            Header header = exc.getResponse().getHeader();

            assertEquals(204, exc.getResponse().getStatusCode());
            assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        /**
         * Normal OPTIONS request
         * @throws URISyntaxException
         */
        @Test
        void preflightRequestWithoutOriginGetsIgnored() throws URISyntaxException {
            Exchange exc = options("/test").buildExchange();

            assertEquals(CONTINUE, i.handleRequest(exc));
            assertNull(exc.getResponse());
        }

        @Test
        void wildcardOriginWithCredentialsShouldBeRejected() throws Exception {
            i.setOrigins("*");
            i.setMethods("GET, POST");
            i.setCredentials(true);

            Exchange exc = createPreflight("https://any.example.com", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void shouldReturnOnlyRequestedMethodInCorsResponse() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("GET, POST");

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST).buildExchange();

            i.handleRequest(exc);

            assertEquals("POST", getAllowMethods(exc));
        }

        @Test
        void shouldAllowNullOriginWhenExplicitlyConfigured() throws Exception {
            i.setOrigins("http://localhost:5173/ null");
            i.setMethods("GET, POST");

            Exchange exc = createPreflight("null", METHOD_POST).buildExchange();

            i.handleRequest(exc);

            assertEquals(204, exc.getResponse().getStatusCode());
        }

        @Test
        void shouldRejectDisallowedHeader() throws Exception {
            i.setOrigins("https://trusted.example.com");

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST,"X-Foo").buildExchange();

            i.handleRequest(exc);

            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void shouldAcceptAllowedHeader() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setHeaders("X-Bar X-Foo X-Zoo");

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST,"X-Foo").buildExchange();

            i.handleRequest(exc);

            assertEquals(204, exc.getResponse().getStatusCode());
        }


        @Test
        void shouldRejectRequestWithDisallowedMethod() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("GET");

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST).buildExchange();

            i.handleRequest(exc);

            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void preflightResponseContainsMaxAge() throws Exception {
            i.setOrigins("https://client.example.com");
            i.setMethods("POST, GET");
            i.setMaxAge("600");

            Exchange exc = createPreflight("https://client.example.com", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals("600", getMaxAge(exc));
        }

        @Test
        void allowAllPreflightRequestShouldSucceed() throws Exception {
            i.setAllowAll(true);
            i.setHeaders("X-Foo");

            Exchange exc = createPreflight("https://any.example.com", METHOD_POST)
                    .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-Bar")
                    .buildExchange();

            i.handleRequest(exc);
            assertTrue(i.isAllowAll());
            assertEquals(204, exc.getResponse().getStatusCode());
            assertEquals("X-Bar", getAllowHeaders(exc));
        }
    }

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

    private static String getAllowOrigin(Exchange exc) {
        return exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    private static String getAllowMethods(Exchange exc) {
        return exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_METHODS);
    }

    private static String getMaxAge(Exchange exc) {
        return exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_MAX_AGE);
    }

    private static String getAllowHeaders(Exchange exc) {
        return exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_HEADERS);
    }

    private static String getAllowCredentials(Exchange exc) {
        return exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS);
    }
}
