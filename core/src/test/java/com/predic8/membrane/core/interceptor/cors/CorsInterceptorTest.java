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

import static com.predic8.membrane.core.http.Header.COOKIE;
import static com.predic8.membrane.core.http.Header.ORIGIN;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.WILDCARD;
import static com.predic8.membrane.core.interceptor.cors.CorsTestUtil.*;
import static com.predic8.membrane.core.interceptor.cors.CorsUtil.*;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.*;

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
            assertEquals(Set.of("foo", "bar", "baz"), i.getAllowedOrigins());
        }

        @Test
        void parseMethodSpaces() {
            i.setMethods("GET, POST");
            assertEquals(Set.of("GET", "POST"), i.getMethods());
        }

        @Test
        void parseHeaderSpaces() {
            i.setHeaders("foo bar baz");
            assertEquals(Set.of("foo", "bar", "baz"), i.getAllowedHeaders());
        }

        @Test
        void credentialsWithOriginWildcard() {
            i.setCredentials(true);
            i.setOrigins("*");
            assertThrows(ConfigurationException.class, () -> i.init());

            i.setOrigins("null http://membrane-api.io *");
            assertThrows(ConfigurationException.class, () -> i.init());
        }
    }

    @Nested
    class NonPreflight {

        /**
         * Nothing should be added to the response header
         */
        @Test
        void withoutOrigin() throws URISyntaxException {
            Exchange exc = callInterceptors(get("/public").buildExchange());

            assertNull(getAllowOrigin(exc));
            assertEquals(emptySet(), getAccessControlAllowHeaderNames(exc.getResponse().getHeader()));
        }

        @Test
        void allowedOrigin() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com");

            Exchange exc = callInterceptors(get("/foo")
                    .header(ORIGIN, "https://trusted.example.com")
                    .buildExchange());

            Header header = exc.getResponse().getHeader();

            checkAllowHeaders(header, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN));
            checkAllowOrigin(header, "https://trusted.example.com");
        }

        @Test
        void disallowedOrigin() throws URISyntaxException {
            i.setOrigins("https://trusted.example.com"); // Overwrite default *

            Exchange exc = callInterceptors(get("/foo")
                    .header(ORIGIN, "https://untrusted.example.com")
                    .buildExchange());

            Header header = exc.getResponse().getHeader();

            checkAllowHeaders(header, emptySet());
        }

        /**
         * Browser will send a preflight. Next call is the post as tested here
         */
        @Test
        void postAfterPreflight() throws Exception {
            Exchange exc = callInterceptors(post("/foo")
                    .contentType(APPLICATION_JSON)
                    .header(ORIGIN,"http://trusted.example.com")
                    .buildExchange());

            assertEquals(200, exc.getResponse().getStatusCode());

            Header h = exc.getResponse().getHeader();

            checkAllowOrigin(h,WILDCARD);
            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void allowAll() throws URISyntaxException {
            i.setAllowAll(true);

            Exchange exc = callInterceptors(post("/test")
                    .header(ORIGIN, "https://evil.example.com")
                    .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-Foo")
                    .buildExchange());

            Header h = exc.getResponse().getHeader();

            System.out.println("h = " + h);

            // Returning * is on purpose!
            assertEquals("*", getAllowOrigin(exc));

            assertTrue(getAllowHeaders(exc).contains("x-foo"));
        }

    }

    @Nested
    class NonPreflightCredentials {

        @Test
        void credentialsCookie() throws URISyntaxException {
            i.setOrigins("http://trusted.example.com");
            i.setCredentials(true);

            Exchange exc = callInterceptors(get("/foo")
                    .header(ORIGIN,"http://trusted.example.com")
                    .header(COOKIE,"sessionid=123")
                    .buildExchange());

            Header h = exc.getResponse().getHeader();

            // Important: The server must not respond with Access-Control-Allow-Origin: * when credentials are used.
            assertEquals("http://trusted.example.com", h.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertEquals("true",h.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));

            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_CREDENTIALS));

        }

        @Test
        void credentialsAuthorization() throws URISyntaxException {
            i.setOrigins("http://trusted.example.com");
            i.setCredentials(true);

            Exchange exc = callInterceptors(get("/foo")
                    .header(ORIGIN,"http://trusted.example.com")
                    .header(AUTHORIZATION,"Bearer 123")
                    .buildExchange());

            Header h = exc.getResponse().getHeader();

            // Important: The server must not respond with Access-Control-Allow-Origin: * when credentials are used.
            assertEquals("http://trusted.example.com", h.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertEquals("true",h.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));

            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_CREDENTIALS));

        }
    }

    private static void checkAllowOrigin(Header header, String origin) {
        assertEquals( origin, header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private static void checkAllowHeaders(Header h, Set<String> allowedHeaders) {
        assertEquals(toLowerCaseSet(allowedHeaders),getAccessControlAllowHeaderNames(h));
    }

    @Nested
    class Preflight {

        @Test
        void allowAllPreflight() throws Exception {
            i.setAllowAll(true);
            i.init();

            Exchange exc = createPreflight("https://evil.example.com", METHOD_POST)
                    .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-Foo")
                    .buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(204, exc.getResponse().getStatusCode());

            assertEquals(METHOD_POST, getAllowMethods(exc));
            assertTrue(getAllowHeaders(exc).contains("x-foo"));
            assertEquals("*", getAllowOrigin(exc));
        }

        @Test
        void explicitlyAllowedNullOrigin() throws Exception {
            i.init();
            i.setOrigins("foo bar null");

            Exchange exc = createPreflight("null", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(204, exc.getResponse().getStatusCode());
        }

        @Test
        void originNullNotAllowed() throws Exception {
            i.init();
            Exchange exc = createPreflight("null", null).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void preflightReturnsOnlyRequestedMethod() throws Exception {
            i.init();
            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(204, exc.getResponse().getStatusCode());
            assertEquals("*", getAllowOrigin(exc));
            assertEquals("POST", getAllowMethods(exc));
        }

        @Test
        void preflightRequestAllowedOrigin() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.init();

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            Header header = exc.getResponse().getHeader();

            assertEquals(204, exc.getResponse().getStatusCode());
            checkAllowOrigin(header, "https://trusted.example.com");
        }

        /**
         * Normal OPTIONS request
         *
         * @throws URISyntaxException
         */
        @Test
        void preflightRequestWithoutOriginGetsIgnored() throws URISyntaxException {
            i.init();
            Exchange exc = options("/test").buildExchange();

            assertEquals(CONTINUE, i.handleRequest(exc));
            assertNull(exc.getResponse());
        }

        @Test
        void wildcardOriginWithCredentialsShouldBeRejected() throws Exception {
            i.setOrigins("*");
            i.setMethods("GET, POST");
            i.init();
            i.setCredentials(true);


            Exchange exc = createPreflight("https://any.example.com", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void shouldReturnOnlyRequestedMethodInCorsResponse() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("GET, POST");
            i.init();

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST).buildExchange();

            i.handleRequest(exc);

            assertEquals("POST", getAllowMethods(exc));
        }

        @Test
        void shouldAllowNullOriginWhenExplicitlyConfigured() throws Exception {
            i.setOrigins("http://localhost:5173/ null");
            i.setMethods("GET, POST");
            i.init();

            Exchange exc = createPreflight("null", METHOD_POST).buildExchange();

            i.handleRequest(exc);

            assertEquals(204, exc.getResponse().getStatusCode());
        }

        @Test
        void shouldRejectDisallowedHeader() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.init();

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST, "X-Foo").buildExchange();

            i.handleRequest(exc);

            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void shouldAcceptAllowedHeader() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setHeaders("X-Bar X-Foo X-Zoo");
            i.init();

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST, "X-Foo").buildExchange();

            i.handleRequest(exc);

            assertEquals(204, exc.getResponse().getStatusCode());
        }


        @Test
        void shouldRejectRequestWithDisallowedMethod() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("GET");
            i.init();

            Exchange exc = createPreflight("https://trusted.example.com", METHOD_POST).buildExchange();

            i.handleRequest(exc);

            assertEquals(403, exc.getResponse().getStatusCode());
        }

        @Test
        void preflightResponseContainsMaxAge() throws Exception {
            i.setOrigins("https://client.example.com");
            i.setMethods("POST, GET");
            i.setMaxAge(600);
            i.init();

            Exchange exc = createPreflight("https://client.example.com", METHOD_POST).buildExchange();

            assertEquals(RETURN, i.handleRequest(exc));
            assertEquals("600", getMaxAge(exc));
        }

        @Test
        void allowAllPreflightRequestShouldSucceed() throws Exception {
            i.setAllowAll(true);
            i.setHeaders("X-Foo");
            i.init();

            Exchange exc = createPreflight("https://any.example.com", METHOD_POST)
                    .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-Bar")
                    .buildExchange();

            i.handleRequest(exc);
            assertTrue(i.isAllowAll());
            assertEquals(204, exc.getResponse().getStatusCode());
            assertEquals("x-bar", getAllowHeaders(exc));
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

    private Exchange callInterceptors(Exchange exc) {
        i.init();
        assertEquals(CONTINUE, i.handleRequest(exc));
        exc.setResponse(ok("OK").build());
        assertEquals(CONTINUE, i.handleResponse(exc));
        return exc;
    }
}
