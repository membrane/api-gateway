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

package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.net.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests URI parsing against the syntax rules and examples from RFC 3986.
 * <p>
 * Sections covered:
 * <ul>
 *   <li>Section 3   - Syntax Components (scheme, authority, path, query, fragment)</li>
 *   <li>Section 3.2 - Authority (userinfo, host, port)</li>
 *   <li>Appendix B  - Parsing a URI Reference with a Regular Expression</li>
 *   <li>Section 1.1.2 - Example URIs</li>
 * </ul>
 *
 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986</a>
 */
class URIRFC3986ComplianceParsingTest {

    // ================================================================
    // Appendix B - The regex ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
    //
    // Group 2: scheme, Group 4: authority, Group 5: path,
    // Group 7: query, Group 9: fragment
    // ================================================================

    @Nested
    @DisplayName("Appendix B - URI Reference Regex Decomposition")
    class AppendixBTests {

        @Test
        @DisplayName("http://www.ics.uci.edu/pub/ietf/uri/#Related (Appendix B example)")
        void appendixBExample() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://www.ics.uci.edu/pub/ietf/uri/#Related", true);
            assertEquals("http", u.getScheme());
            assertEquals("www.ics.uci.edu", u.getAuthority());
            assertEquals("/pub/ietf/uri/", u.getRawPath());
            assertNull(u.getRawQuery());
            assertEquals("Related", u.getRawFragment());
        }

        @Test
        @DisplayName("Scheme, authority, path, query, and fragment all present")
        void allComponentsPresent() throws URISyntaxException {
            URI u = new com.predic8.membrane.core.util.URI("http://host/path?query#fragment", true);
            assertEquals("http", u.getScheme());
            assertEquals("host", u.getAuthority());
            assertEquals("/path", u.getRawPath());
            assertEquals("query", u.getRawQuery());
            assertEquals("fragment", u.getRawFragment());
        }

        @Test
        @DisplayName("Only scheme and path (no authority, no query, no fragment)")
        void schemeAndPathOnly() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("mailto:user@example.com", true);
            assertEquals("mailto", u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("user@example.com", u.getRawPath());
            assertNull(u.getRawQuery());
            assertNull(u.getRawFragment());
        }

        @Test
        @DisplayName("Authority present but empty path")
        void authorityEmptyPath() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host", true);
            assertEquals("http", u.getScheme());
            assertEquals("host", u.getAuthority());
            assertEquals("", u.getRawPath());
            assertNull(u.getRawQuery());
            assertNull(u.getRawFragment());
        }

        @Test
        @DisplayName("No scheme (relative reference with authority)")
        void noSchemeWithAuthority() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("//host/path", true);
            assertNull(u.getScheme());
            assertEquals("host", u.getAuthority());
            assertEquals("/path", u.getRawPath());
        }

        @Test
        @DisplayName("No scheme, no authority (relative path reference)")
        void relativePathOnly() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("path/to/resource", true);
            assertNull(u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("path/to/resource", u.getRawPath());
        }

        @Test
        @DisplayName("Query only (no path, no scheme)")
        void queryOnly() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("?query", true);
            assertNull(u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("", u.getRawPath());
            assertEquals("query", u.getRawQuery());
        }

        @Test
        @DisplayName("Fragment only")
        void fragmentOnly() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("#fragment", true);
            assertNull(u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("", u.getRawPath());
            assertNull(u.getRawQuery());
            assertEquals("fragment", u.getRawFragment());
        }

        @Test
        @DisplayName("Empty string parses as empty path")
        void emptyString() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("", true);
            assertNull(u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("", u.getRawPath());
            assertNull(u.getRawQuery());
            assertNull(u.getRawFragment());
        }
    }

    // ================================================================
    // Section 3 - Syntax Components
    // ================================================================

    @Nested
    @DisplayName("Section 3 - Full URI Decomposition: foo://example.com:8042/over/there?name=ferret#nose")
    class Section3Example {

        private com.predic8.membrane.core.util.URI u;

        @BeforeEach
        void setUp() throws URISyntaxException {
            u = new com.predic8.membrane.core.util.URI("foo://example.com:8042/over/there?name=ferret#nose", true);
        }

        @Test
        void scheme() {
            assertEquals("foo", u.getScheme());
        }

        @Test
        void authority() {
            assertEquals("example.com:8042", u.getAuthority());
        }

        @Test
        void host() {
            assertEquals("example.com", u.getHost());
        }

        @Test
        void port() {
            assertEquals(8042, u.getPort());
        }

        @Test
        void path() {
            assertEquals("/over/there", u.getRawPath());
        }

        @Test
        void query() {
            assertEquals("name=ferret", u.getRawQuery());
        }

        @Test
        void fragment() {
            assertEquals("nose", u.getRawFragment());
        }
    }

    // ================================================================
    // Section 3.1 - Scheme
    // ================================================================

    @Nested
    @DisplayName("Section 3.1 - Scheme")
    class SchemeTests {

        @Test
        @DisplayName("Simple lowercase scheme")
        void lowercaseScheme() throws URISyntaxException {
            assertEquals("http", new com.predic8.membrane.core.util.URI("http://host", true).getScheme());
        }

        @Test
        @DisplayName("Uppercase scheme (schemes are case-insensitive)")
        void uppercaseScheme() throws URISyntaxException {
            assertEquals("HTTP", new com.predic8.membrane.core.util.URI("HTTP://host", true).getScheme());
        }

        @Test
        @DisplayName("Scheme with digits, plus, period, hyphen")
        void schemeWithSpecialChars() throws URISyntaxException {
            assertEquals("coap+tcp", new com.predic8.membrane.core.util.URI("coap+tcp://host/path", true).getScheme());
        }

        @Test
        @DisplayName("Scheme with period and hyphen")
        void schemeWithDotAndHyphen() throws URISyntaxException {
            assertEquals("a.b-c", new com.predic8.membrane.core.util.URI("a.b-c://host", true).getScheme());
        }

        @Test
        @DisplayName("Single letter scheme")
        void singleLetterScheme() throws URISyntaxException {
            assertEquals("x", new com.predic8.membrane.core.util.URI("x://host", true).getScheme());
        }

        @Test
        @DisplayName("No scheme in relative reference")
        void noScheme() throws URISyntaxException {
            assertNull(new com.predic8.membrane.core.util.URI("/path", true).getScheme());
        }
    }

    // ================================================================
    // Section 3.2 - Authority
    // ================================================================

    @Nested
    @DisplayName("Section 3.2 - Authority")
    class AuthorityTests {

        @Test
        @DisplayName("Authority with host only")
        void hostOnly() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://example.com/path", true);
            assertEquals("example.com", u.getAuthority());
            assertEquals("example.com", u.getHost());
            assertEquals(-1, u.getPort());
        }

        @Test
        @DisplayName("Authority with host and port")
        void hostAndPort() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://example.com:8080/path", true);
            assertEquals("example.com:8080", u.getAuthority());
            assertEquals("example.com", u.getHost());
            assertEquals(8080, u.getPort());
        }

        @Test
        @DisplayName("Authority with userinfo, host, and port")
        void userinfoHostPort() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://user:pass@example.com:8080/path", true);
            assertEquals("user:pass@example.com:8080", u.getAuthority());
            assertEquals("example.com", u.getHost());
            assertEquals(8080, u.getPort());
        }

        @Test
        @DisplayName("No authority (scheme:path form)")
        void noAuthority() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("mailto:user@example.com", true);
            assertNull(u.getAuthority());
        }

        @Test
        @DisplayName("Empty authority (file:///path) - custom parser rejects empty host")
        void emptyAuthority() {
            // RFC 3986 allows empty authority (e.g. file:///path), but the custom
            // parser requires a non-empty host, so this throws.
            assertThrows(IllegalArgumentException.class,
                    () -> new com.predic8.membrane.core.util.URI("file:///etc/hosts", true));
        }

        @Test
        @DisplayName("Authority stops at slash")
        void authorityStopsAtSlash() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host/path", true);
            assertEquals("host", u.getAuthority());
            assertEquals("/path", u.getRawPath());
        }

        @Test
        @DisplayName("Authority stops at question mark")
        void authorityStopsAtQuestion() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host?query", true);
            assertEquals("host", u.getAuthority());
            assertEquals("", u.getRawPath());
            assertEquals("query", u.getRawQuery());
        }

        @Test
        @DisplayName("Authority stops at hash")
        void authorityStopsAtHash() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host#fragment", true);
            assertEquals("host", u.getAuthority());
            assertEquals("", u.getRawPath());
            assertEquals("fragment", u.getRawFragment());
        }
    }

    // ================================================================
    // Section 3.2.2 - Host (IPv6)
    // ================================================================

    @Nested
    @DisplayName("Section 3.2.2 - IPv6 Host Parsing")
    class IPv6HostTests {

        @Test
        @DisplayName("IPv6 address in brackets")
        void ipv6Basic() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://[2001:db8::1]/path", true);
            assertEquals("[2001:db8::1]", u.getHost());
            assertEquals(-1, u.getPort());
        }

        @Test
        @DisplayName("IPv6 with port")
        void ipv6WithPort() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://[2001:db8::1]:8080/path", true);
            assertEquals("[2001:db8::1]", u.getHost());
            assertEquals(8080, u.getPort());
        }

        @Test
        @DisplayName("IPv6 loopback")
        void ipv6Loopback() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://[::1]/path", true);
            assertEquals("[::1]", u.getHost());
        }

        @Test
        @DisplayName("IPv6 with zone ID (percent-encoded)")
        void ipv6WithZoneId() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://[fe80::1%25eth0]:1234/path", true);
            assertEquals("[fe80::1%25eth0]", u.getHost());
            assertEquals(1234, u.getPort());
        }
    }

    // ================================================================
    // Section 3.2.3 - Port
    // ================================================================

    @Nested
    @DisplayName("Section 3.2.3 - Port")
    class PortTests {

        @Test
        @DisplayName("Default port not specified returns -1")
        void noPort() throws URISyntaxException {
            assertEquals(-1, new com.predic8.membrane.core.util.URI("http://host/path", true).getPort());
        }

        @Test
        @DisplayName("Port 80")
        void port80() throws URISyntaxException {
            assertEquals(80, new com.predic8.membrane.core.util.URI("http://host:80/path", true).getPort());
        }

        @Test
        @DisplayName("Port 443")
        void port443() throws URISyntaxException {
            assertEquals(443, new com.predic8.membrane.core.util.URI("https://host:443/path", true).getPort());
        }

        @Test
        @DisplayName("Port 0 (minimum)")
        void portZero() throws URISyntaxException {
            assertEquals(0, new com.predic8.membrane.core.util.URI("http://host:0/path", true).getPort());
        }

        @Test
        @DisplayName("Port 65535 (maximum)")
        void portMax() throws URISyntaxException {
            assertEquals(65535, new com.predic8.membrane.core.util.URI("http://host:65535/path", true).getPort());
        }

        @Test
        @DisplayName("High port number")
        void highPort() throws URISyntaxException {
            assertEquals(49152, new com.predic8.membrane.core.util.URI("http://host:49152/path", true).getPort());
        }
    }

    // ================================================================
    // Section 3.3 - Path
    // ================================================================

    @Nested
    @DisplayName("Section 3.3 - Path")
    class PathTests {

        @Test
        @DisplayName("path-abempty: empty path with authority")
        void pathAbemptyEmpty() throws URISyntaxException {
            assertEquals("", new com.predic8.membrane.core.util.URI("http://host", true).getRawPath());
        }

        @Test
        @DisplayName("path-abempty: slash path with authority")
        void pathAbemptySlash() throws URISyntaxException {
            assertEquals("/", new com.predic8.membrane.core.util.URI("http://host/", true).getRawPath());
        }

        @Test
        @DisplayName("path-abempty: multi-segment path")
        void pathAbemptyMultiSegment() throws URISyntaxException {
            assertEquals("/a/b/c", new com.predic8.membrane.core.util.URI("http://host/a/b/c", true).getRawPath());
        }

        @Test
        @DisplayName("path-absolute: starts with / but no authority")
        void pathAbsolute() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("/path/to/resource", true);
            assertNull(u.getAuthority());
            assertEquals("/path/to/resource", u.getRawPath());
        }

        @Test
        @DisplayName("path-rootless: no leading slash, no authority")
        void pathRootless() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("relative/path", true);
            assertNull(u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("relative/path", u.getRawPath());
        }

        @Test
        @DisplayName("path-empty: no path at all")
        void pathEmpty() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host", true);
            assertEquals("", u.getRawPath());
        }

        @Test
        @DisplayName("Path with semicolon parameters (RFC 3986 treats as opaque path data)")
        void pathWithSemicolon() throws URISyntaxException {
            assertEquals("/b/c/d;p", new com.predic8.membrane.core.util.URI("http://a/b/c/d;p?q", true).getRawPath());
        }

        @Test
        @DisplayName("Trailing slash in path")
        void trailingSlash() throws URISyntaxException {
            assertEquals("/path/", new com.predic8.membrane.core.util.URI("http://host/path/", true).getRawPath());
        }

        @Test
        @DisplayName("Single slash path")
        void singleSlash() throws URISyntaxException {
            assertEquals("/", new com.predic8.membrane.core.util.URI("/", true).getRawPath());
        }
    }

    // ================================================================
    // Section 3.4 - Query
    // ================================================================

    @Nested
    @DisplayName("Section 3.4 - Query")
    class QueryTests {

        @Test
        @DisplayName("Simple query")
        void simpleQuery() throws URISyntaxException {
            assertEquals("key=value", new com.predic8.membrane.core.util.URI("http://host/path?key=value", true).getRawQuery());
        }

        @Test
        @DisplayName("Multiple query parameters")
        void multipleParams() throws URISyntaxException {
            assertEquals("a=1&b=2", new com.predic8.membrane.core.util.URI("http://host/path?a=1&b=2", true).getRawQuery());
        }

        @Test
        @DisplayName("Query can contain slashes and question marks (per RFC 3986 Section 3.4)")
        void queryWithSlashesAndQuestionMarks() throws URISyntaxException {
            assertEquals("objectClass?one",
                    new com.predic8.membrane.core.util.URI("ldap://[2001:db8::7]/c=GB?objectClass?one", true).getRawQuery());
        }

        @Test
        @DisplayName("Empty query (just question mark)")
        void emptyQuery() throws URISyntaxException {
            assertEquals("", new com.predic8.membrane.core.util.URI("http://host/path?", true).getRawQuery());
        }

        @Test
        @DisplayName("No query returns null")
        void noQuery() throws URISyntaxException {
            assertNull(new com.predic8.membrane.core.util.URI("http://host/path", true).getRawQuery());
        }

        @Test
        @DisplayName("Query with fragment following")
        void queryBeforeFragment() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host/path?query#frag", true);
            assertEquals("query", u.getRawQuery());
            assertEquals("frag", u.getRawFragment());
        }
    }

    // ================================================================
    // Section 3.5 - Fragment
    // ================================================================

    @Nested
    @DisplayName("Section 3.5 - Fragment")
    class FragmentTests {

        @Test
        @DisplayName("Simple fragment")
        void simpleFragment() throws URISyntaxException {
            assertEquals("section1", new com.predic8.membrane.core.util.URI("http://host/path#section1", true).getRawFragment());
        }

        @Test
        @DisplayName("Fragment can contain slashes and question marks")
        void fragmentWithSlashesAndQuestionMarks() throws URISyntaxException {
            assertEquals("s/./x", new com.predic8.membrane.core.util.URI("http://host/path#s/./x", true).getRawFragment());
        }

        @Test
        @DisplayName("Empty fragment (just hash)")
        void emptyFragment() throws URISyntaxException {
            assertEquals("", new com.predic8.membrane.core.util.URI("http://host/path#", true).getRawFragment());
        }

        @Test
        @DisplayName("No fragment returns null")
        void noFragment() throws URISyntaxException {
            assertNull(new com.predic8.membrane.core.util.URI("http://host/path", true).getRawFragment());
        }

        @Test
        @DisplayName("Fragment after query")
        void fragmentAfterQuery() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host/path?q=1#frag", true);
            assertEquals("q=1", u.getRawQuery());
            assertEquals("frag", u.getRawFragment());
        }

        @Test
        @DisplayName("Fragment without query")
        void fragmentWithoutQuery() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host/path#frag", true);
            assertNull(u.getRawQuery());
            assertEquals("frag", u.getRawFragment());
        }
    }

    // ================================================================
    // Section 1.1.2 - Example URIs
    // ================================================================

    @Nested
    @DisplayName("Section 1.1.2 - Example URIs from the RFC")
    class Section112Examples {

        @Test
        @DisplayName("ftp://ftp.is.co.za/rfc/rfc1808.txt")
        void ftpUri() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("ftp://ftp.is.co.za/rfc/rfc1808.txt", true);
            assertEquals("ftp", u.getScheme());
            assertEquals("ftp.is.co.za", u.getAuthority());
            assertEquals("/rfc/rfc1808.txt", u.getRawPath());
            assertNull(u.getRawQuery());
            assertNull(u.getRawFragment());
        }

        @Test
        @DisplayName("http://www.ietf.org/rfc/rfc2396.txt")
        void httpUri() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://www.ietf.org/rfc/rfc2396.txt", true);
            assertEquals("http", u.getScheme());
            assertEquals("www.ietf.org", u.getAuthority());
            assertEquals("/rfc/rfc2396.txt", u.getRawPath());
        }

        @Test
        @DisplayName("ldap://[2001:db8::7]/c=GB?objectClass?one")
        void ldapUri() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("ldap://[2001:db8::7]/c=GB?objectClass?one", true);
            assertEquals("ldap", u.getScheme());
            assertEquals("[2001:db8::7]", u.getAuthority());
            assertEquals("[2001:db8::7]", u.getHost());
            assertEquals("/c=GB", u.getRawPath());
            assertEquals("objectClass?one", u.getRawQuery());
        }

        @Test
        @DisplayName("mailto:John.Doe@example.com")
        void mailtoUri() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("mailto:John.Doe@example.com", true);
            assertEquals("mailto", u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("John.Doe@example.com", u.getRawPath());
        }

        @Test
        @DisplayName("news:comp.infosystems.www.servers.unix")
        void newsUri() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("news:comp.infosystems.www.servers.unix", true);
            assertEquals("news", u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("comp.infosystems.www.servers.unix", u.getRawPath());
        }

        @Test
        @DisplayName("tel:+1-816-555-1212")
        void telUri() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("tel:+1-816-555-1212", true);
            assertEquals("tel", u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("+1-816-555-1212", u.getRawPath());
        }

        @Test
        @DisplayName("telnet://192.0.2.16:80/")
        void telnetUri() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("telnet://192.0.2.16:80/", true);
            assertEquals("telnet", u.getScheme());
            assertEquals("192.0.2.16:80", u.getAuthority());
            assertEquals("192.0.2.16", u.getHost());
            assertEquals(80, u.getPort());
            assertEquals("/", u.getRawPath());
        }

        @Test
        @DisplayName("urn:oasis:names:specification:docbook:dtd:xml:4.1.2")
        void urnUri() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("urn:oasis:names:specification:docbook:dtd:xml:4.1.2", true);
            assertEquals("urn", u.getScheme());
            assertNull(u.getAuthority());
            assertEquals("oasis:names:specification:docbook:dtd:xml:4.1.2", u.getRawPath());
        }
    }

    // ================================================================
    // Section 5.4 Base URI - parsing of the base used in resolution
    // ================================================================

    @Nested
    @DisplayName("Section 5.4 - Base URI Parsing: http://a/b/c/d;p?q")
    class Section54BaseUri {

        private com.predic8.membrane.core.util.URI u;

        @BeforeEach
        void setUp() throws URISyntaxException {
            u = new com.predic8.membrane.core.util.URI("http://a/b/c/d;p?q", true);
        }

        @Test
        void scheme() {
            assertEquals("http", u.getScheme());
        }

        @Test
        void authority() {
            assertEquals("a", u.getAuthority());
        }

        @Test
        void host() {
            assertEquals("a", u.getHost());
        }

        @Test
        void port() {
            assertEquals(-1, u.getPort());
        }

        @Test
        void path() {
            assertEquals("/b/c/d;p", u.getRawPath());
        }

        @Test
        void query() {
            assertEquals("q", u.getRawQuery());
        }

        @Test
        void fragment() {
            assertNull(u.getRawFragment());
        }
    }

    // ================================================================
    // Percent-encoding awareness (Section 2.1)
    // ================================================================

    @Nested
    @DisplayName("Section 2.1 - Percent-Encoding in Components")
    class PercentEncodingTests {

        @Test
        @DisplayName("Percent-encoded path is preserved raw")
        void percentEncodedPath() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host/a%20b", true);
            assertEquals("/a%20b", u.getRawPath());
            assertEquals("/a b", u.getPath());
        }

        @Test
        @DisplayName("Percent-encoded query is preserved raw")
        void percentEncodedQuery() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host/path?key=a%20b", true);
            assertEquals("key=a%20b", u.getRawQuery());
        }

        @Test
        @DisplayName("Percent-encoded fragment is preserved raw")
        void percentEncodedFragment() throws URISyntaxException {
            com.predic8.membrane.core.util.URI u = new com.predic8.membrane.core.util.URI("http://host/path#a%20b", true);
            assertEquals("a%20b", u.getRawFragment());
            assertEquals("a b", u.getFragment());
        }
    }

    // ================================================================
    // Parameterized: Section 1.1.2 examples for scheme and authority
    // ================================================================

    @DisplayName("Section 1.1.2 - Parameterized scheme parsing")
    @ParameterizedTest(name = "\"{0}\" -> scheme=\"{1}\"")
    @MethodSource("schemeExamples")
    void schemeExtraction(String input, String expectedScheme) throws URISyntaxException {
        assertEquals(expectedScheme, new com.predic8.membrane.core.util.URI(input, true).getScheme());
    }

    static Stream<Arguments> schemeExamples() {
        return Stream.of(
                Arguments.of("ftp://ftp.is.co.za/rfc/rfc1808.txt", "ftp"),
                Arguments.of("http://www.ietf.org/rfc/rfc2396.txt", "http"),
                Arguments.of("ldap://[2001:db8::7]/c=GB?objectClass?one", "ldap"),
                Arguments.of("mailto:John.Doe@example.com", "mailto"),
                Arguments.of("news:comp.infosystems.www.servers.unix", "news"),
                Arguments.of("tel:+1-816-555-1212", "tel"),
                Arguments.of("telnet://192.0.2.16:80/", "telnet"),
                Arguments.of("urn:oasis:names:specification:docbook:dtd:xml:4.1.2", "urn"),
                Arguments.of("https://example.com", "https"),
                Arguments.of("foo://example.com:8042/over/there?name=ferret#nose", "foo")
        );
    }

    @DisplayName("Section 1.1.2 - Parameterized authority parsing")
    @ParameterizedTest(name = "\"{0}\" -> authority=\"{1}\"")
    @MethodSource("authorityExamples")
    void authorityExtraction(String input, String expectedAuthority) throws URISyntaxException {
        assertEquals(expectedAuthority, new com.predic8.membrane.core.util.URI(input, true).getAuthority());
    }

    static Stream<Arguments> authorityExamples() {
        return Stream.of(
                Arguments.of("ftp://ftp.is.co.za/rfc/rfc1808.txt", "ftp.is.co.za"),
                Arguments.of("http://www.ietf.org/rfc/rfc2396.txt", "www.ietf.org"),
                Arguments.of("ldap://[2001:db8::7]/c=GB?objectClass?one", "[2001:db8::7]"),
                Arguments.of("telnet://192.0.2.16:80/", "192.0.2.16:80"),
                Arguments.of("foo://example.com:8042/over/there?name=ferret#nose", "example.com:8042"),
                Arguments.of("http://user:pass@host:8080/path", "user:pass@host:8080")
        );
    }

    @DisplayName("Section 1.1.2 - Parameterized path parsing")
    @ParameterizedTest(name = "\"{0}\" -> path=\"{1}\"")
    @MethodSource("pathExamples")
    void pathExtraction(String input, String expectedPath) throws URISyntaxException {
        assertEquals(expectedPath, new com.predic8.membrane.core.util.URI(input, true).getRawPath());
    }

    static Stream<Arguments> pathExamples() {
        return Stream.of(
                Arguments.of("ftp://ftp.is.co.za/rfc/rfc1808.txt", "/rfc/rfc1808.txt"),
                Arguments.of("http://www.ietf.org/rfc/rfc2396.txt", "/rfc/rfc2396.txt"),
                Arguments.of("ldap://[2001:db8::7]/c=GB?objectClass?one", "/c=GB"),
                Arguments.of("mailto:John.Doe@example.com", "John.Doe@example.com"),
                Arguments.of("news:comp.infosystems.www.servers.unix", "comp.infosystems.www.servers.unix"),
                Arguments.of("tel:+1-816-555-1212", "+1-816-555-1212"),
                Arguments.of("telnet://192.0.2.16:80/", "/"),
                Arguments.of("urn:oasis:names:specification:docbook:dtd:xml:4.1.2",
                        "oasis:names:specification:docbook:dtd:xml:4.1.2"),
                Arguments.of("foo://example.com:8042/over/there?name=ferret#nose", "/over/there"),
                Arguments.of("http://host", ""),
                Arguments.of("/absolute/path", "/absolute/path"),
                Arguments.of("relative/path", "relative/path"),
                Arguments.of("", "")
        );
    }
}
