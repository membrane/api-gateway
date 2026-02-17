/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static com.predic8.membrane.core.util.URLUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class URLUtilTest {

    @Test
    void authority() {
        assertEquals("a", getAuthority("internal:a"));
        assertEquals("a", getAuthority("internal://a"));
        assertEquals("a", getAuthority("a"));
        assertEquals("a", getAuthority("a/b"));
        assertEquals("a", getAuthority("internal:a/b"));
        assertEquals("a", getAuthority("internal://a/b"));
        assertEquals("localhost", getAuthority("http://localhost"));
        assertEquals("localhost:8080", getAuthority("http://localhost:8080"));
        assertEquals("localhost:80", getAuthority("http://localhost:80/foo"));
    }

    @Test
    void testCreateQueryString() {
        assertEquals("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1",
                createQueryString("endpoint", "http://node1.clustera",
                        "cluster", "c1"));

    }

    @Test
    void testParseQueryString() {
        assertEquals("http://node1.clustera", parseQueryString("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1", ERROR).get("endpoint"));
        assertEquals("c1", parseQueryString("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1", ERROR).get("cluster"));
    }

    @Test
    void testParamsWithoutValueString() {
        assertEquals("jim", parseQueryString("name=jim&male", ERROR).get("name"));
        assertEquals("", parseQueryString("name=jim&male", ERROR).get("male"));
        assertEquals("", parseQueryString("name=anna&age=", ERROR).get("age"));
    }

    @Test
    void testDecodePath() throws Exception {
        URI u = new com.predic8.membrane.core.util.URI( "/path/to%20my/resource",true);
        assertEquals("/path/to my/resource", u.getPath());
        assertEquals("/path/to%20my/resource", u.getRawPath());
    }

    @Test
    void getPortFromURLTest() throws MalformedURLException {
        assertEquals(2000, getPortFromURL(new URL("http://localhost:2000")));
        assertEquals(80, getPortFromURL(new URL("http://localhost")));
        assertEquals(443, getPortFromURL(new URL("https://api.predic8.de")));
    }

    @Test
    void testGetNameComponent() throws Exception {
        assertEquals("", getNameComponent(new URIFactory(), ""));
        assertEquals("", getNameComponent(new URIFactory(), "/"));
        assertEquals("foo", getNameComponent(new URIFactory(), "foo"));
        assertEquals("foo", getNameComponent(new URIFactory(), "/foo"));
        assertEquals("bar", getNameComponent(new URIFactory(), "/foo/bar"));
        assertEquals("bar", getNameComponent(new URIFactory(), "foo/bar"));
        assertEquals("", getNameComponent(new URIFactory(), "foo/bar/"));
    }

    @Nested
    class PathSeg {

        record Case(Object in, String expected) {
        }

        static Stream<Case> cases() {
            return Stream.of(
                    // null + empties
                    new Case(null, ""),
                    new Case("", ""),

                    // unreserved kept
                    new Case("AZaz09-._~", "AZaz09-._~"),

                    // common reserved characters
                    new Case(" ", "%20"),
                    new Case("a b", "a%20b"),
                    new Case("&", "%26"),
                    new Case("a&b", "a%26b"),
                    new Case("/", "%2F"),
                    new Case("a/b", "a%2Fb"),
                    new Case("?", "%3F"),
                    new Case("#", "%23"),
                    new Case("=", "%3D"),
                    new Case(":", "%3A"),
                    new Case("@", "%40"),
                    new Case(";", "%3B"),
                    new Case(",", "%2C"),
                    new Case("+", "%2B"),

                    // traversal-like input should not create subpaths
                    new Case("../", "..%2F"),
                    new Case("../../admin", "..%2F..%2Fadmin"),

                    // percent must be encoded (prevents smuggling pre-encoded delimiters)
                    new Case("%", "%25"),
                    new Case("%2F", "%252F"),
                    new Case("100% legit", "100%25%20legit"),

                    // control characters (log safety)
                    new Case("a\nb", "a%0Ab"),
                    new Case("a\rb", "a%0Db"),
                    new Case("\t", "%09"),

                    // quotes and braces
                    new Case("\"", "%22"),
                    new Case("'", "%27"),
                    new Case("{", "%7B"),
                    new Case("}", "%7D"),

                    // utf-8
                    new Case("ä", "%C3%A4"),
                    new Case("€", "%E2%82%AC"),
                    new Case("😀", "%F0%9F%98%80"),
                    new Case("日本", "%E6%97%A5%E6%9C%AC"),

                    // non-string object
                    new Case(123, "123")
            );
        }

        @ParameterizedTest(name = "[{index}] in={0} => {1}")
        @MethodSource("cases")
        @DisplayName("pathSeg encodes as RFC3986 path segment")
        void encodesExpected(Case c) {
            assertEquals(c.expected(),  URLUtil.pathSeg(c.in()));
        }

        record AllowedCase(Object in) {
        }

        static Stream<AllowedCase> allowedCases() {
            return Stream.of(
                    new AllowedCase("simple"),
                    new AllowedCase("a/b c?d=e&f#g"),
                    new AllowedCase("../../admin"),
                    new AllowedCase("100% legit"),
                    new AllowedCase("äöü😀\n\r\t")
            );
        }

        @ParameterizedTest(name = "[{index}] allowed charset for in={0}")
        @MethodSource("allowedCases")
        @DisplayName("pathSeg output contains only unreserved characters or percent-escapes")
        void outputAllowedCharactersOnly(AllowedCase c) {
            String out = URLUtil.pathSeg(c.in());
            assertTrue(out.matches("[A-Za-z0-9\\-._~%]*"), out);

            // If '%' appears, it must be followed by two hex digits
            for (int i = 0; i < out.length(); i++) {
                if (out.charAt(i) == '%') {
                    assertTrue(i + 2 < out.length(), "Dangling % at end: " + out);
                    assertTrue(isHex(out.charAt(i + 1)) && isHex(out.charAt(i + 2)),
                            "Invalid percent-escape at pos " + i + ": " + out);
                    i += 2;
                }
            }
        }

        private static boolean isHex(char ch) {
            return (ch >= '0' && ch <= '9') ||
                   (ch >= 'A' && ch <= 'F') ||
                   (ch >= 'a' && ch <= 'f');
        }
    }
}
