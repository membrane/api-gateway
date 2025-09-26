/* Copyright 2011 predic8 GmbH, www.predic8.com

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

import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.transport.http.HostColonPort.parse;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
public class HostColonPortTest {

    static final HostColonPort HCP_LOCALHOST = new HostColonPort(false, "localhost", 80);
    static final HostColonPort HCP_LOCALHOST_8080 = new HostColonPort(false, "localhost", 8080);
    static final HostColonPort HCP_HTTPS_LOCALHOST = new HostColonPort(true, "localhost", 443);
    static final HostColonPort HCP_HTTPS_LOCALHOST_8443 = new HostColonPort(true, "localhost", 8443);

	static URI uriLocalhost, uriLocalhost8080, uriHttpsLocalhost, uriHttpsLocalhost8443;

    @BeforeAll
    static void setUp() throws URISyntaxException {
        uriLocalhost = new java.net.URI("http", null, "localhost", 80, null, null, null);
        uriLocalhost8080 = new java.net.URI("http", null, "localhost", 8080, null, null, null);
        uriHttpsLocalhost = new java.net.URI("https", null, "localhost", 443, null, null, null);
        uriHttpsLocalhost8443 = new java.net.URI("https", null, "localhost", 8443, null, null, null);
    }

    @Test
    void testDefaultPort() {
        HostColonPort hcp = new HostColonPort(false, "predic8.com");
        assertEquals("predic8.com", hcp.host());
        assertEquals(80, hcp.port());
    }

    @Test
    void testGetHost() {
        assertEquals("predic8.com", new HostColonPort(false, "predic8.com:80").host());
    }

    @Test
    void testGetPort() {
        assertEquals(80, new HostColonPort(false, "predic8.com:80").port());
    }

    @Test
    void noNumber() {
        Assertions.assertThrowsExactly(NumberFormatException.class, () -> new HostColonPort(false, "foo:no-number"));
    }

    @Test
    void getProtocol() {
        assertEquals("http", new HostColonPort("foo", 80).getProtocol());
        assertEquals("https", new HostColonPort("foo", 443).getProtocol());
    }

    @Test
    void getUrl() {
        assertEquals("http://foo:80", new HostColonPort("foo", 80).getUrl());
        assertEquals("https://foo:443", new HostColonPort("foo", 443).getUrl());
    }

    @Test
    void toURITests() throws URISyntaxException {
        assertEquals(uriLocalhost, HCP_LOCALHOST.toURI());
        assertEquals(uriLocalhost8080, HCP_LOCALHOST_8080.toURI());
        assertEquals(uriHttpsLocalhost, HCP_HTTPS_LOCALHOST.toURI());
        assertEquals(uriHttpsLocalhost8443, HCP_HTTPS_LOCALHOST_8443.toURI());
    }

    @Nested
    class Parse {

        @Test
        @DisplayName("Parse HTTPS URL with explicit port")
        void httpsWithPort() throws Exception {
            HostColonPort result = parse("https://api.example.com:8080");

            assertTrue(result.useSSL());
            assertEquals("api.example.com", result.host());
            assertEquals(8080, result.port());
        }

        @Test
        @DisplayName("Parse HTTPS URL without port (should default to 443)")
        void httpsWithoutPort() throws Exception {
            HostColonPort result = parse("https://secure.example.com");

            assertTrue(result.useSSL());
            assertEquals("secure.example.com", result.host());
            assertEquals(443, result.port()); // Default HTTPS port
        }

        @Test
        @DisplayName("Parse HTTP URL with explicit port")
        void httpWithPort() throws Exception {
            HostColonPort result = parse("http://api.example.com:3000");

            assertFalse(result.useSSL());
            assertEquals("api.example.com", result.host());
            assertEquals(3000, result.port());
        }

        @Test
        @DisplayName("Parse HTTP URL without port (should default to 80)")
        void testHttpWithoutPort() throws MalformedURLException {
            HostColonPort result = parse("http://example.com");

            assertFalse(result.useSSL());
            assertEquals("example.com", result.host());
            assertEquals(80, result.port()); // Default HTTP port
        }

        @Test
        @DisplayName("Parse URLs with different host formats")
        void differentHostFormats() throws Exception {
            // Localhost
            HostColonPort localhost = parse("http://localhost:8080");
            assertEquals("localhost", localhost.host());

            // IP address
            HostColonPort ipResult = parse("https://192.168.1.1:443");
            assertEquals("192.168.1.1", ipResult.host());

            // Subdomain
            HostColonPort subdomain = parse("https://api.sub.example.com");
            assertEquals("api.sub.example.com", subdomain.host());
        }

        @Test
        void errors() {
            assertThrows(MalformedURLException.class, () -> parse("invalid-url-format"));
            assertThrows(MalformedURLException.class, () -> parse("https://"));
            assertThrows(MalformedURLException.class, () -> parse("invalid://"));
        }

        @Test
        @DisplayName("Handle URLs with paths, queries, and fragments")
        void urlsWithAdditionalComponents() throws Exception {
            // URL with path
            HostColonPort withPath = parse("https://api.example.com:8080/v1/users");
            assertTrue(withPath.useSSL());
            assertEquals("api.example.com", withPath.host());
            assertEquals(8080, withPath.port());

            // URL with query parameters
            HostColonPort withQuery = parse("http://search.com/search?q=test");
            assertFalse(withQuery.useSSL());
            assertEquals("search.com", withQuery.host());
            assertEquals(80, withQuery.port());

            // URL with fragment
            HostColonPort withFragment = parse("https://docs.example.com#section1");
            assertTrue(withFragment.useSSL());
            assertEquals("docs.example.com", withFragment.host());
            assertEquals(443, withFragment.port());
        }

        @Test
        @DisplayName("Test port boundary values")
        void portBoundaryValues() throws Exception {
            // Valid port ranges (1-65535)
            HostColonPort minPort = parse("http://example.com:1");
            assertEquals(1, minPort.port());

            HostColonPort maxPort = parse("http://example.com:65535");
            assertEquals(65535, maxPort.port());

            // The parser doesn't validate port ranges, so these will parse successfully
            // but may be invalid from a networking perspective
            HostColonPort zeroPort = parse("http://example.com:0");
            assertEquals(0, zeroPort.port());
        }

        @Test
        @DisplayName("Test that only 'https' scheme triggers SSL")
        void sslDetection() throws Exception {
            // Only "https" should trigger SSL
            assertTrue(parse("https://example.com").useSSL());
            assertFalse(parse("http://example.com").useSSL());
        }
    }
}
