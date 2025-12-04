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

import tools.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.COOKIE;
import static com.predic8.membrane.core.http.Header.ORIGIN;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.cors.AbstractCORSHandler.*;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static com.predic8.membrane.core.interceptor.cors.CorsTestUtil.*;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.VARY;
import static org.springframework.http.HttpHeaders.*;

class CorsInterceptorTest {

    ObjectMapper om = new ObjectMapper();

    CorsInterceptor i;

    @BeforeEach
    void setup() {
        i = new CorsInterceptor();
    }

    @Nested
    class HelperMethods {

        @Test
        void parseOriginSpaces() {
            i.setOrigins("http://foo.example.com http://bar.example.com http://baz.example.com");
            assertEquals(Set.of("http://foo.example.com", "http://bar.example.com", "http://baz.example.com"), i.getAllowedOrigins());
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
            i.setOrigins(WILDCARD);
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

            Header h = exc.getResponse().getHeader();

            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN));
            checkAllowOrigin(h, "https://trusted.example.com");
            assertEquals(ORIGIN, h.getFirstValue(VARY));
            assertNull(h.getFirstValue(ACCESS_CONTROL_MAX_AGE));
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
                    .header(ORIGIN, "http://trusted.example.com")
                    .buildExchange());

            assertEquals(200, exc.getResponse().getStatusCode());

            Header h = exc.getResponse().getHeader();

            checkAllowOrigin(h, WILDCARD);
            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void allowAll() throws URISyntaxException {
            i.setAllowAll(true);

            Exchange exc = callInterceptors(post("/test")
                    .header(ORIGIN, "https://evil.example.com")
                    .buildExchange());

            // Returning WILDCARD is on purpose!
            assertEquals(WILDCARD, getAllowOrigin(exc));
        }

        @Test
        void exposeHeaders() throws URISyntaxException {
            i.setAllowAll(true);
            i.setExposeHeaders("X-Request-ID, ETag");

            Exchange exc = callInterceptors(get("/foo")
                    .header(ORIGIN, "https://untrusted.example.com")
                    .buildExchange());

            assertEquals(CONTINUE, i.handleRequest(exc));
            exc.setResponse(ok("OK")
                    .header("X-Request-ID", "99")
                    .header("ETag", "32443")
                    .build());
            assertEquals(CONTINUE, i.handleResponse(exc));

            Header h = exc.getResponse().getHeader();
            assertEquals("x-request-id, etag", h.getFirstValue(ACCESS_CONTROL_EXPOSE_HEADERS));

            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN));
        }

    }

    @Nested
    class NonPreflightCredentials {

        @Test
        void credentialsCookie() throws URISyntaxException {
            i.setOrigins("http://trusted.example.com");
            i.setCredentials(true);

            Exchange exc = callInterceptors(get("/foo")
                    .header(ORIGIN, "http://trusted.example.com")
                    .header(COOKIE, "sessionid=123")
                    .buildExchange());

            Header h = exc.getResponse().getHeader();

            // Important: The server must not respond with Access-Control-Allow-Origin: * when credentials are used.
            assertEquals("http://trusted.example.com", h.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertEquals("true", h.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));

            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_CREDENTIALS));

        }

        @Test
        void credentialsAuthorization() throws URISyntaxException {
            i.setOrigins("http://trusted.example.com");
            i.setCredentials(true);

            Exchange exc = callInterceptors(get("/foo")
                    .header(ORIGIN, "http://trusted.example.com")
                    .header(AUTHORIZATION, "Bearer 123")
                    .buildExchange());

            Header h = exc.getResponse().getHeader();

            // Important: The server must not respond with Access-Control-Allow-Origin: * when credentials are used.
            assertEquals("http://trusted.example.com", h.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
            assertEquals("true", h.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));

            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_CREDENTIALS));

        }
    }

    @Nested
    class Preflight {

        @Test
        void allowAllWithHeaderPreflight() throws Exception {
            i.setAllowAll(true);
            i.init();

            Exchange exc = makePreflight(createPreflight("https://evil.example.com", METHOD_POST)
                    .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-Foo"));

            Header h = exc.getResponse().getHeader();

            assertEquals(METHOD_POST, getAllowMethods(exc));
            assertTrue(getAllowHeaders(exc).contains("X-Foo"));
            assertEquals(WILDCARD, getAllowOrigin(exc));

            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_METHODS, ACCESS_CONTROL_ALLOW_HEADERS));

            assertNotNull(h.getFirstValue(ACCESS_CONTROL_MAX_AGE));
        }

        @Test
        void explicitlyAllowedNullOriginPreflight() throws Exception {
            i.init();
            i.setOrigins("http://foo.example.com https://bar.example.com null");

            Exchange exc = makePreflight(createPreflight("null", METHOD_POST));

            Header h = exc.getResponse().getHeader();
            System.out.println("h = " + h);

            assertEquals(METHOD_POST, getAllowMethods(exc));
            assertEquals(NULL_STRING, getAllowOrigin(exc));
            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_METHODS));
        }

        @Test
        void originNullNotAllowedPreflight() throws Exception {
            i.init();
            Exchange exc = makePreflight(createPreflight("null", METHOD_POST), 403);

            assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());

            JsonNode jn = om.readTree(exc.getResponse().getBodyAsStringDecoded());
            assertEquals("https://membrane-api.io/problems/security/origin-not-allowed", jn.get("type").asText());

            Header h = exc.getResponse().getHeader();
            checkAllowHeaders(h, emptySet());
        }

        @Test
        void returnOnlyRequestedMethodPreflight() throws Exception {
            i.init();

            Exchange exc = makePreflight(createPreflight("https://trusted.example.com", METHOD_POST));

            Header h = exc.getResponse().getHeader();

            checkAllowOrigin(h, WILDCARD);
            assertEquals("POST", getAllowMethods(exc));
            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_METHODS));
        }


        @Test
        void requestAllowedOriginPreflight() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.init();

            Exchange exc = makePreflight(createPreflight("https://trusted.example.com", METHOD_POST));

            Header h = exc.getResponse().getHeader();

            checkAllowOrigin(h, "https://trusted.example.com");
            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_METHODS));
        }

        /**
         * Normal OPTIONS request
         *
         * @throws URISyntaxException
         */
        @Test
        void withoutOriginPreflight() throws Exception {
            i.init();

            // Request without origin on purpose!
            Exchange exc = options("/test").header(ACCESS_CONTROL_REQUEST_METHOD, METHOD_POST).buildExchange();

            // Call handleRequest in test on purpose!
            assertEquals(CONTINUE, i.handleRequest(exc));

            assertNull(exc.getResponse());
        }

        @Test
        void wildcardOriginWithCredentialsShouldBeRejectedPreflight() {
            i.setOrigins(WILDCARD);
            i.setCredentials(true);
            assertThrows(ConfigurationException.class, () -> i.init());
        }


        @Test
        void disallowedHeaderPreflight() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.init();

            Exchange exc = makePreflight(createPreflight("https://any.example.com", METHOD_POST).header("X-Not-Allowed", "Bar"), 403);

            JsonNode jn = om.readTree(exc.getResponse().getBodyAsStringDecoded());
            assertEquals("https://membrane-api.io/problems/security/origin-not-allowed", jn.get("type").asText());

            Header h = exc.getResponse().getHeader();
            checkAllowHeaders(h, emptySet());
        }

        @Test
        void allowedHeaderPreflight() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setHeaders("X-Bar X-Foo X-Zoo");
            i.init();

            Exchange exc = makePreflight(createPreflight("https://trusted.example.com", METHOD_POST, "X-Foo"));

            Header h = exc.getResponse().getHeader();

            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_METHODS, ACCESS_CONTROL_ALLOW_HEADERS));
        }

        @Test
        void multipleMethodPreflight() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("PUT, POST, DELETE, PATCH");
            i.init();

            Exchange exc = makePreflight(createPreflight("https://trusted.example.com", METHOD_POST));

            Header h = exc.getResponse().getHeader();
            assertEquals(METHOD_POST, getAllowMethods(exc));
            checkAllowHeaders(h, Set.of(ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_METHODS));

        }


        @Test
        void disallowedMethodPreflight() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setMethods(METHOD_PUT);
            i.init();

            Exchange exc = makePreflight(createPreflight("https://trusted.example.com", METHOD_POST), 403);

            JsonNode jn = om.readTree(exc.getResponse().getBodyAsStringDecoded());
            assertEquals("https://membrane-api.io/problems/security/method-not-allowed", jn.get("type").asText());

            Header h = exc.getResponse().getHeader();
            checkAllowHeaders(h, emptySet());
        }

        @Test
        void preflightContainsMaxAgePreflight() throws Exception {
            i.setOrigins("https://trusted.example.com");
            i.setMethods("POST, GET");
            i.setMaxAge(600);
            i.init();

            Exchange exc = makePreflight(createPreflight("https://trusted.example.com", METHOD_POST));
            assertEquals("600", getMaxAge(exc));
        }

        @Test
        void doNotExposeHeadersInPreflight() throws Exception {
            i.setExposeHeaders("X-Custom");
            Exchange exc = makePreflight(createPreflight("https://trusted.example.com", METHOD_POST));
            Header h = exc.getResponse().getHeader();
            assertNull(h.getFirstValue(ACCESS_CONTROL_EXPOSE_HEADERS));
        }

        @ParameterizedTest
        @CsvSource({
                "http://example.com,              http://example.com",
                "http://example.com/,             http://example.com",
                "http://example.com:80,           http://example.com",
                "https://example.com,             https://example.com",
                "https://sub.example.com,         https://sub.example.com",
                "https://example.com:443/,        https://example.com",
                "http://example.com:8080,         http://example.com:8080",
                "https://EXAMPLE.COM:443,         https://example.com"
        })
        void shouldNormalizeOrigins(String origin, String expected) {
            i.setOrigins(origin);
            assertEquals(expected, i.getOrigins());
        }
    }

    private Exchange makePreflight(Builder builder) {
        return makePreflight(builder, 204);
    }

    private Exchange makePreflight(Builder builder, int expectedStatus) {
        i.init();
        Exchange exc = builder.buildExchange();
        assertEquals(RETURN, i.handleRequest(exc));
        assertEquals(expectedStatus, exc.getResponse().getStatusCode());
        return exc;
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

    private Exchange callInterceptors(Exchange exc) {
        i.init();
        assertEquals(CONTINUE, i.handleRequest(exc));
        exc.setResponse(ok("OK").build());
        assertEquals(CONTINUE, i.handleResponse(exc));
        return exc;
    }

    private static void checkAllowOrigin(Header header, String origin) {
        assertEquals(origin, header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private static void checkAllowHeaders(Header h, Set<String> allowedHeaders) {
        assertEquals(CollectionsUtil.toLowerCaseSet(allowedHeaders), getAccessControlAllowHeaderNames(h));
    }
}
