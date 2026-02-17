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

package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.util.URI.*;
import static org.junit.jupiter.api.Assertions.*;

class URITest {

    private static URI URI_ALLOW_ILLEGAL;

    @BeforeAll
    static void init() throws URISyntaxException {
        URI_ALLOW_ILLEGAL = new URI("dummy", true);
    }

    @SuppressWarnings("UnnecessaryUnicodeEscape")
    @Test
    void testEncoding() {
        assertError("htt\u00E4p://predic8.de/path/file?a=query#foo", "/path/file", "a=query");
        assertError("htt%C3%A4p://predic8.de/path/file?a=query#foo", "/path/file", "a=query");
    }

    @Test
    void illegalCharacter() {
        assertError("http://predic8.de/test?a=q{uery#foo", "/test", "a=q{uery");
        assertError("http://predic8.de/te{st?a=query#foo", "/te{st", "a=query");
    }

    @Test
    void getScheme() throws URISyntaxException {
        checkGetSchemeCustomParsing(false);
    }

    @Test
    void getSchemeCustom() throws URISyntaxException {
        checkGetSchemeCustomParsing(true);
    }

    private void checkGetSchemeCustomParsing(boolean custom) throws URISyntaxException {
        assertEquals("http", new URI("http://predic8.de", custom).getScheme());
        assertEquals("https", new com.predic8.membrane.core.util.URI("https://predic8.de", custom).getScheme());
    }

    @Test
    void getHost() throws URISyntaxException {
        checkGetHost(false);
    }

    @Test
    void getHostCustom() throws URISyntaxException {
        checkGetHost(true);
    }

    private void checkGetHost(boolean custom) throws URISyntaxException {
        assertEquals("predic8.de", new URI("http://predic8.de/foo", custom).getHost());
        assertEquals("predic8.de", new com.predic8.membrane.core.util.URI("http://user:pwd@predic8.de:8080/foo", custom).getHost());
        assertEquals("predic8.de", new com.predic8.membrane.core.util.URI("http://predic8.de:8080/foo", custom).getHost());
        assertEquals("predic8.de", new com.predic8.membrane.core.util.URI("https://predic8.de/foo", custom).getHost());
        assertEquals("predic8.de", new URI("https://predic8.de:8443/foo", custom).getHost());
    }

    @Test
    void getPort() throws URISyntaxException {
        getPortCustomParsing(false);
    }

    @Test
    void getPortCustom() throws URISyntaxException {
        getPortCustomParsing(true);
    }

    /**
     * Default port should be returned as unknown.
     */
    @Test
    void urlStandardBehaviour() throws URISyntaxException {
        assertEquals(-1, new java.net.URI("http://predic8.de/foo").getPort());
    }

    private void getPortCustomParsing(boolean custom) throws URISyntaxException {
        assertEquals(-1, new com.predic8.membrane.core.util.URI("http://predic8.de/foo", custom).getPort());
        assertEquals(-1, new com.predic8.membrane.core.util.URI("https://predic8.de/foo", custom).getPort());
        assertEquals(8090, new com.predic8.membrane.core.util.URI("http://predic8.de:8090/foo", custom).getPort());
        assertEquals(8443, new URI("https://predic8.de:8443/foo", custom).getPort());
        assertEquals(8090, new URI("http://user:pwd@predic8.de:8090/foo", custom).getPort());
        assertEquals(8443, new URI("https://user:pwd@predic8.de:8443/foo", custom).getPort());
    }

    private void assertError(String uri, String path, String query) {
        try {
            new com.predic8.membrane.core.util.URI(uri);
            fail("Expected URISyntaxException.");
        } catch (URISyntaxException | IllegalArgumentException e) {
            // do nothing
        }
        try {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI(uri, true);
            assertEquals(path, u.getPath());
            assertEquals(query, u.getQuery());
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("UnnecessaryUnicodeEscape")
    @Test
    void encoding() {
        assertError("htt\u00E4p://predic8.de/path/file?a=query#foo", "/path/file", "a=query");
        assertError("htt%C3%A4p://predic8.de/path/file?a=query#foo", "/path/file", "a=query");
    }

    @Test
    void withoutPath() throws URISyntaxException {
        URIFactory uf = new URIFactory(true);
        assertEquals("http://localhost", uf.create("http://localhost").getWithoutPath());
        assertEquals("http://localhost:8080", uf.create("http://localhost:8080").getWithoutPath());
        assertEquals("http://localhost:8080", uf.create("http://localhost:8080/foo").getWithoutPath());
        assertEquals("http://localhost:8080", uf.create("http://localhost:8080#foo").getWithoutPath());
        assertEquals("http://localhost", uf.create("http://localhost/foo").getWithoutPath());
        assertEquals("http://localhost", uf.create("http://localhost/foo").getWithoutPath());
        assertEquals("http://localhost", uf.create("http://localhost/foo").getWithoutPath());
    }

    @Test
    void testRemoveDotSegments() {
        assertEquals("a", removeDotSegments("../a"));
        assertEquals("a", removeDotSegments("./a"));
        assertEquals("a/b", removeDotSegments("a/./b"));
        assertEquals("/b", removeDotSegments("a/../b"));
        assertEquals("a/c", removeDotSegments("a/b/../c"));
        assertEquals("/a", removeDotSegments("/../a"));
    }

    @Nested
    class Authority {
        @Test
        void getAuthorityCustom() throws URISyntaxException {
            checkGetAuthority(true);
        }

        @Test
        void getAuthorityDefault() throws URISyntaxException {
            checkGetAuthority(false);
        }

        private void checkGetAuthority(boolean custom) throws URISyntaxException {
            // plain host
            assertEquals("predic8.de", new com.predic8.membrane.core.util.URI("http://predic8.de/foo", custom).getAuthority());

            // host + port
            assertEquals("predic8.de:8080", new URI("http://predic8.de:8080/foo", custom).getAuthority());

            // with userinfo
            assertEquals("user:pwd@predic8.de:8080",
                    new com.predic8.membrane.core.util.URI("http://user:pwd@predic8.de:8080/foo", custom).getAuthority());

            // https with port
            assertEquals("predic8.de:8443", new URI("https://predic8.de:8443/foo", custom).getAuthority());

            // https without port
            assertEquals("predic8.de", new URI("https://predic8.de/foo", custom).getAuthority());

            // IPv6 with port
            assertEquals("[2001:db8::1]:8080", new com.predic8.membrane.core.util.URI("http://[2001:db8::1]:8080/foo", custom).getAuthority());

            // no authority present (mailto)
            assertNull(new com.predic8.membrane.core.util.URI("mailto:alice@example.com", custom).getAuthority());

            // IPv6 with port and userinfo
            assertEquals("user:pwd@[2001:db8::1]:9090",
                    new com.predic8.membrane.core.util.URI("http://user:pwd@[2001:db8::1]:9090/foo", custom).getAuthority());
        }

        // No IPv6 support in custom parsing
        @Test
        void getAuthorityIPv6Custom() throws URISyntaxException {
            assertEquals("[2001:db8::1]", new URI("http://[2001:db8::1]/foo", false).getAuthority());
            assertEquals("[2001:db8::1]:8080", new com.predic8.membrane.core.util.URI("http://[2001:db8::1]:8080/foo", false).getAuthority());
        }
    }

    @Test
    void getPathWithQuery() throws URISyntaxException {
        assertEquals("/", new URIFactory().create("").getPathWithQuery());
        assertEquals("/foo", new URIFactory().create("http://localhost/foo").getPathWithQuery());
        assertEquals("/foo?q=1", new URIFactory().create("/foo?q=1").getPathWithQuery());
        assertEquals("/", new URIFactory().create("http://localhost").getPathWithQuery());
        assertEquals("/foo?", new URIFactory().create("/foo?").getPathWithQuery());
    }

    @Test
    @DisplayName("Fragments should be removed and not propagated to backend")
    void removeFragment() throws URISyntaxException {
        assertEquals("/foo", new URIFactory().create("http://localhost:777/foo#frag").getPathWithQuery());
        assertEquals("/", new URIFactory().create("#frag").getPathWithQuery());
        assertEquals("/foo?q=1", new URIFactory().create("/foo?q=1#frag").getPathWithQuery());
    }

    @Test
    void getPathWithQuery_keep_raw() throws URISyntaxException {
        assertEquals("/foo?q=a%20b", new URIFactory().create("/foo?q=a%20b").getPathWithQuery());
        assertEquals("/", new URIFactory().create("#a%20b").getPathWithQuery());
        assertEquals("/foo?q=a+b", new URIFactory().create("/foo?q=a+b").getPathWithQuery()); // '+' must remain '+'
        assertEquals("/foo", new URIFactory().create("/foo#c%2Fd").getPathWithQuery());  // '/' in fragment is encoded
    }

    @Nested
    class ParsingUtilitiesTests {

        @Test
        void parsePortWorks() {
            assertEquals(8080, parsePort(":8080"));
            assertEquals(0, parsePort(":0"));
            assertEquals(65535, parsePort(":65535"));
            assertThrows(IllegalArgumentException.class, () -> parsePort(":-1"));
            assertThrows(IllegalArgumentException.class, () -> parsePort(":100000"));
            assertThrows(IllegalArgumentException.class, () -> parsePort(":foo"));
            assertThrows(IllegalArgumentException.class, () -> parsePort("xyz"));
        }

        @Test
        void stripUserInfoWorks() {
            assertEquals("example.com", stripUserInfo("user:pass@example.com"));
            assertEquals("example.com", stripUserInfo("example.com"));
            assertEquals("", stripUserInfo("user@"));
        }

        @Test
        void isIPv6() {
            assertTrue(isIP6Literal("[::1]"));
            assertTrue(isIP6Literal("[::1"));
            assertFalse(isIP6Literal("::1"));
        }
    }

    @Nested
    class HostPortParsingTests {

        @Test
        void parseHostPortNullOrEmpty() {
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort(null));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort(""));
        }

        @Test
        void parseHostPortWithIPv4AndPort() {
            com.predic8.membrane.core.util.URI.HostPort hp = URI_ALLOW_ILLEGAL.parseHostPort("example.com:8080");
            assertEquals("example.com", hp.host());
            assertEquals(8080, hp.port());
        }

        @Test
        void parseHostPortWithIPv6() {
            com.predic8.membrane.core.util.URI.HostPort hp = URI_ALLOW_ILLEGAL.parseHostPort("[2001:db8::1]:9090");
            assertEquals("[2001:db8::1]", hp.host());
            assertEquals(9090, hp.port());
        }

        @Test
        void parseIpv6WithoutPort() {
            com.predic8.membrane.core.util.URI.HostPort hp = parseIpv6("[::1]");
            assertEquals("[::1]", hp.host());
            assertEquals(-1, hp.port());
        }

        @Test
        void parseIpv6WithZoneId() {
            URI.HostPort hp = parseIpv6("[fe80::1%25eth0]:1234");
            assertEquals("[fe80::1%25eth0]", hp.host());
            assertEquals(1234, hp.port());
        }

        @Test
        void parseIpv6InvalidCases() {
            assertThrows(IllegalArgumentException.class, () -> parseIpv6("::1"));
            assertThrows(IllegalArgumentException.class, () -> parseIpv6("[::1"));
            assertThrows(IllegalArgumentException.class, () -> parseIpv6("[::1]:"));
            assertThrows(IllegalArgumentException.class, () -> parseIpv6("[::1]:badport"));
        }

        @Test
        void parseHostPortIpv4WithoutPort() {
            com.predic8.membrane.core.util.URI.HostPort hp = URI_ALLOW_ILLEGAL.parseIPv4OrHostname("example.com");
            assertEquals("example.com", hp.host());
            assertEquals(-1, hp.port());
        }

        @Test
        void parseHostPortIpv4InvalidCases() {
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseIPv4OrHostname(":8080"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseIPv4OrHostname("example.com:"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseIPv4OrHostname("example.com:abc"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseIPv4OrHostname("host:1:2"));
        }

        @Test
        void parseHostPortStripsUserInfoForIpv4() {
            URI.HostPort hp = URI_ALLOW_ILLEGAL.parseHostPort("user:pwd@example.com:8080");
            assertEquals("example.com", hp.host());
            assertEquals(8080, hp.port());
        }

        @Test
        void parseHostPortStripsUserInfoForIpv6() {
            URI.HostPort hp = URI_ALLOW_ILLEGAL.parseHostPort("user:pwd@[2001:db8::1]:443");
            assertEquals("[2001:db8::1]", hp.host());
            assertEquals(443, hp.port());
        }

        @Test
        void parseHostPortRejectsEmptyHostAfterUserInfo() {
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("user@"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("user@:8080"));
        }

        @Test
        void parseHostPortIpv4NoPortReturnsNoPort() {
            URI.HostPort hp = URI_ALLOW_ILLEGAL.parseHostPort("example.com");
            assertEquals("example.com", hp.host());
            assertEquals(-1, hp.port());
        }

        @Test
        void parseHostPortInvalidMultipleColons() {
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("host:1:2"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("[::1]:1:2"));
        }

        @Test
        void parseHostPortIpv4EmptyPortOrHost() {
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort(":8080"));       // empty host
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("example.com:")); // empty port
        }

        @Test
        void parseHostPortIpv4PortBoundsAndFormats() {
            assertEquals(0, URI_ALLOW_ILLEGAL.parseHostPort("example.com:0").port());
            assertEquals(65535, URI_ALLOW_ILLEGAL.parseHostPort("example.com:65535").port());
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("example.com:-1"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("example.com:65536"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("example.com:abc"));
        }

        @Test
        void parseHostPortIpv6WithoutPort() {
            com.predic8.membrane.core.util.URI.HostPort hp = URI_ALLOW_ILLEGAL.parseHostPort("[2001:db8::1]");
            assertEquals("[2001:db8::1]", hp.host());
            assertEquals(-1, hp.port());
        }

        @Test
        void parseHostPortIpv6WithZoneIdNormalization() {
            com.predic8.membrane.core.util.URI.HostPort hp = URI_ALLOW_ILLEGAL.parseHostPort("[fe80::1%25eth0]:1234");
            assertEquals("[fe80::1%25eth0]", hp.host());
            assertEquals(1234, hp.port());
        }

        @Test
        void parseHostPortIpv6BadPortAndJunk() {
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("[::1]:"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("[::1]:bad"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("[::1]x123"));
        }

        @Test
        void parseHostPortIpv6EmptyHostRejected() {
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("[]"));
            assertThrows(IllegalArgumentException.class, () -> URI_ALLOW_ILLEGAL.parseHostPort("[]:80"));
        }

        @Test
        void parseHostPortRespectsUppercaseHexAndCompressed() {
            assertEquals("[2001:DB8:0:0::1]", URI_ALLOW_ILLEGAL.parseHostPort("[2001:DB8:0:0::1]").host());
            assertEquals("[2001:db8::1]", URI_ALLOW_ILLEGAL.parseHostPort("[2001:db8::1]:8080").host());
        }
    }


    @Nested
    class IPv6Tests {

        @Test
        void withoutPort() throws URISyntaxException {
            URI u = new URI("http://[2001:db8::1]", true);
            assertEquals("[2001:db8::1]", u.getHost());
            assertEquals(-1, u.getPort());
        }

        @Test
        void withPort() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://[2001:db8::1]:8080", true);
            assertEquals("[2001:db8::1]", u.getHost());
            assertEquals(8080, u.getPort());
        }

        @Test
        void withPath() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://[2001:db8::1]/foo", true);
            assertEquals("[2001:db8::1]", u.getHost());
            assertEquals(-1, u.getPort());
            assertEquals("/foo", u.getPath());
        }

        @Test
        void invalid() {
            assertThrows(IllegalArgumentException.class, () -> new com.predic8.membrane.core.util.URI("http://[2001:db8::1/foo", true));
        }

        @Test
        void withPortAndPath() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new URI("http://[2001:db8::1]:8080/foo", true);
            assertEquals("[2001:db8::1]", u.getHost());
            assertEquals(8080, u.getPort());
            assertEquals("/foo", u.getPath());
        }

        @Test
        void withUserInfo() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://user:pwd@[2001:db8::1]:8080/foo", false);
            assertEquals("[2001:db8::1]", u.getHost());
            assertEquals(8080, u.getPort());
            assertEquals("/foo", u.getPath());
            // java.net.URI includes userinfo
            assertEquals("user:pwd@[2001:db8::1]:8080", u.getAuthority());
        }

        @Test
        void withoutUserInfo() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://[2001:db8::1]:8080/foo", true);
            assertEquals("[2001:db8::1]", u.getHost());
            assertEquals(8080, u.getPort());
            assertEquals("/foo", u.getPath());
            assertEquals("[2001:db8::1]:8080", u.getAuthority());
        }

        @Test
        void withZoneIdNormalized() throws URISyntaxException {
            URI u = new URI("http://[fe80::1%25eth0]:1234/foo", true);
            assertEquals("[fe80::1%25eth0]", u.getHost());
            assertEquals(1234, u.getPort());
            assertEquals("/foo", u.getPath());
            assertEquals("[fe80::1%25eth0]:1234", u.getAuthority());
        }

        @Test
        void withZoneIdNormalized2() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new URI("http://[fe80::1%25eth0]:1234/foo", false);
            assertEquals("[fe80::1%25eth0]", u.getHost());
            assertEquals(1234, u.getPort());
            assertEquals("/foo", u.getPath());
            assertEquals("[fe80::1%25eth0]:1234", u.getAuthority());
        }

        @Test
        void authorityFormattingWithAndWithoutPort() throws URISyntaxException {
            assertEquals("[2001:db8::1]", new com.predic8.membrane.core.util.URI("http://[2001:db8::1]/x", true).getAuthority());
            assertEquals("[2001:db8::1]:8080", new com.predic8.membrane.core.util.URI("http://[2001:db8::1]:8080/x", true).getAuthority());
        }

        @Test
        void pathQueryAndFragmentHandling() throws URISyntaxException {
            assertEquals("/foo?q=1",
                    new URIFactory().create("http://[2001:db8::1]:7777/foo?q=1#frag").getPathWithQuery());
        }

        @Test
        void portLowerAndUpperBounds() throws URISyntaxException {
            URI u1 = new URI("http://[2001:db8::1]:0/foo", true);
            assertEquals(0, u1.getPort());

            URI u2 = new com.predic8.membrane.core.util.URI("http://[2001:db8::1]:65535/foo", true);
            assertEquals(65535, u2.getPort());
        }

        @Test
        void portOutOfRangeOrNonNumeric() {
            assertThrows(IllegalArgumentException.class, () -> new URI("http://[2001:db8::1]:65536/foo", true));
            assertThrows(IllegalArgumentException.class, () -> new com.predic8.membrane.core.util.URI("http://[2001:db8::1]:-1/foo", true));
            assertThrows(IllegalArgumentException.class, () -> new URI("http://[2001:db8::1]:abcd/foo", true));
        }

        @Test
        void invalidJunkAfterBracket() {
            // anything after ']' must be ':' + digits or the end
            assertThrows(IllegalArgumentException.class, () -> new URI("http://[2001:db8::1]x123/foo", true));
        }

        @Test
        void uppercaseHexAndCompressedForms() throws URISyntaxException {
            assertEquals("[2001:DB8:0:0::1]", new URI("http://[2001:DB8:0:0::1]/", true).getHost());
            assertEquals("[2001:db8::1]", new URI("http://[2001:db8::1]/", true).getHost());
        }

        @Test
        void emptyHostIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> new URI("http://[]:80/", true));
            assertThrows(IllegalArgumentException.class, () -> new URI("http://[]/", true));
        }
    }

    @Nested
    class ResolveTests {

        @Test
        @DisplayName("Resolve relative path against standard URI base")
        void resolveStandardBase() throws URISyntaxException {
            URI base = new com.predic8.membrane.core.util.URI( "http://example.com",false);
            URI relative = new com.predic8.membrane.core.util.URI( "/foo/bar",false);
            assertEquals("http://example.com/foo/bar", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve relative path against standard URI base with trailing slash")
        void resolveStandardBaseTrailingSlash() throws URISyntaxException {
            com.predic8.membrane.core.util.URI base = new com.predic8.membrane.core.util.URI( "http://example.com/",false);
            com.predic8.membrane.core.util.URI relative = new URI( "/foo/bar",false);
            assertEquals("http://example.com/foo/bar", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve with query string on relative URI")
        void resolveWithQuery() throws URISyntaxException {
            com.predic8.membrane.core.util.URI base = new URI("http://example.com",false);
            URI relative = new URI( "/foo?q=1",false);
            assertEquals("http://example.com/foo?q=1", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve empty relative path - standard URI returns base with trailing slash")
        void resolveEmptyRelativeStandard() throws URISyntaxException {
            URI base = new URI( "http://example.com/basepath",false);
            com.predic8.membrane.core.util.URI relative = new URI( "");
            // Behaviour according to RFC 3986. Deviates from java.net.URI
            assertEquals("http://example.com/basepath", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve with port in base URI")
        void resolveWithPort() throws URISyntaxException {
            URI base = new URI("http://example.com:8080");
            URI relative = new URI( "/api/test");
            assertEquals("http://example.com:8080/api/test", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve relative path against custom-parsed base with illegal characters (placeholder)")
        void resolveCustomParsedPlaceholderHost() throws URISyntaxException {
            com.predic8.membrane.core.util.URI base = new com.predic8.membrane.core.util.URI("http://${placeholder}", true);
            URI relative = new com.predic8.membrane.core.util.URI("/foo/bar", true);
            assertEquals("http://${placeholder}/foo/bar", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve with query against custom-parsed base with illegal characters")
        void resolveCustomParsedPlaceholderWithQuery() throws URISyntaxException {
            URI base = new com.predic8.membrane.core.util.URI("http://${placeholder}", true);
            com.predic8.membrane.core.util.URI relative = new com.predic8.membrane.core.util.URI("/foo?q=1", true);
            assertEquals("http://${placeholder}/foo?q=1", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve empty relative keeps base path in custom parsing mode")
        void resolveCustomParsedEmptyRelative() throws URISyntaxException {
            com.predic8.membrane.core.util.URI base = new URI("http://${placeholder}/basepath", true);
            URI relative = new com.predic8.membrane.core.util.URI("", true);
            assertEquals("http://${placeholder}/basepath", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve with port in custom-parsed base with illegal characters")
        void resolveCustomParsedPlaceholderWithPort() throws URISyntaxException {
            URI base = new com.predic8.membrane.core.util.URI("http://${placeholder}:8080", true);
            com.predic8.membrane.core.util.URI relative = new com.predic8.membrane.core.util.URI("/api/test", true);
            assertEquals("http://${placeholder}:8080/api/test", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve using URIFactory with allowIllegalCharacters")
        void resolveViaURIFactory() throws URISyntaxException {
            URIFactory factory = new URIFactory(true);
            com.predic8.membrane.core.util.URI base = factory.create("http://${host}");
            URI relative = factory.create("/path");
            assertEquals("http://${host}/path", base.resolve(relative).toString());
        }

        @Test
        @DisplayName("Resolve with curly braces in path of base")
        void resolveCustomParsedCurlyBracesInPath() throws URISyntaxException {
            URI base = new com.predic8.membrane.core.util.URI("http://example.com/${version}", true);
            com.predic8.membrane.core.util.URI relative = new URI("/foo", true);
            assertEquals("http://example.com/foo", base.resolve(relative).toString());
        }

        @Test
        void resolveStandardWithQueryOnRelative() throws URISyntaxException {
            URI base = new URI("https://api.example.com");
            com.predic8.membrane.core.util.URI relative = new com.predic8.membrane.core.util.URI( "/v1/resource?key=value");
            assertEquals("https://api.example.com/v1/resource?key=value", base.resolve(relative).toString());
        }

        @Test
        void resolveCustomParsedHttps() throws URISyntaxException {
            com.predic8.membrane.core.util.URI base = new com.predic8.membrane.core.util.URI("https://${host}", true);
            com.predic8.membrane.core.util.URI relative = new com.predic8.membrane.core.util.URI("/secure/path", true);
            assertEquals("https://${host}/secure/path", base.resolve(relative).toString());
        }

        @Test
        void resolveRelativeWithPathBack() throws URISyntaxException {
            com.predic8.membrane.core.util.URI base = new URI( "http://localhost/validation");
            URI relative = new URI( "../validation/ArticleType.xsd");
            assertEquals("http://localhost/validation/ArticleType.xsd", base.resolve(relative).toString());
        }

        @Test
        void resolveRelativeWithPathBackClasspath() throws URISyntaxException {
            URI base = new com.predic8.membrane.core.util.URI( "classpath://authority/validation");
            URI relative = new URI("../validation/ArticleType.xsd");
            assertEquals("classpath://authority/../validation/ArticleType.xsd", base.resolve(relative).toString());
        }

        @Test
        void resolveRelativeBackClasspath() throws URISyntaxException {
            URI base = new URI("classpath://validation");
            URI relative = new com.predic8.membrane.core.util.URI("../validation/ArticleType.xsd");
            // getRessource() can deal with that
            assertEquals("classpath://validation/../validation/ArticleType.xsd", base.resolve(relative).toString());
        }
    }
}
