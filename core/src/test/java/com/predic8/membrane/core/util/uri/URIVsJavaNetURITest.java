/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.uri;

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.net.*;
import java.util.stream.*;

import static com.predic8.membrane.core.util.URIFactory.*;
import static org.junit.jupiter.api.Assertions.*;

class URIVsJavaNetURITest {

    record Case(String input) {
    }

    static Stream<Case> urisThatShouldMatchJavaNetURI() {
        return Stream.of(
                new Case("http://example.com"),
                new Case("http://example.com/"),
                new Case("http://example.com/basepath"),
                new Case("http://example.com/base/path"),
                new Case("http://example.com/base/path?x=1&y=2"),
                new Case("http://example.com/base/path?x=1&y=2#frag"),
                new Case("http://user:pass@example.com:8080/a/b?x=1#f"),
                new Case("https://example.com:443/a%20b?x=%2F#c%20d"),
                new Case("http://[2001:db8::1]/p?q=1#f"),
                new Case("http://[fe80::1%25eth0]/p?q=1#f")
        );
    }

    @ParameterizedTest
    @MethodSource("urisThatShouldMatchJavaNetURI")
    void shouldMatchJavaNetURIForCommonCases(Case c) throws Exception {
        var custom = DEFAULT_URI_FACTORY.create(c.input());
        var j = new java.net.URI(c.input());

        // These should match for typical hierarchical URIs.
        assertEquals(j.getScheme(), custom.getScheme(), "scheme");
        assertEquals(j.getRawAuthority(), custom.getAuthority(), "authority (raw, as in input)");
        assertEquals(j.getRawPath(), custom.getRawPath(), "rawPath");
        assertEquals(j.getRawQuery(), custom.getRawQuery(), "rawQuery");
        assertEquals(j.getRawFragment(), custom.getRawFragment(), "rawFragment");

        assertEquals(j.getPath(), custom.getPath(), "path (decoded)");
        assertEquals(j.getQuery(), custom.getQuery(), "query (decoded)");
        assertEquals(j.getFragment(), custom.getFragment(), "fragment (decoded)");

        assertEquals(j.getHost(), custom.getHost(), "host (java host is bracket-free)");
        assertEquals(j.getPort(), custom.getPort(), "port");

        // getPathWithQuery is Membrane-specific; compare to Java reconstruction.
        assertEquals(expectedPathWithQueryFromJava(j), custom.getPathWithQuery(), "pathWithQuery");
    }

    @Test
    void resolvesLikeJavaNetURIForHttp() throws Exception {
        assertResolveMatchesJava("http://example.com/basepath", "x");
        assertResolveMatchesJava("http://example.com/basepath/", "x");
        assertResolveMatchesJava("http://example.com/base/dir/", "../x");
        assertResolveMatchesJava("http://example.com/base/dir/", "./x");
        assertResolveMatchesJava("http://example.com/base/dir/", "../../x");
        assertResolveMatchesJava("http://example.com/base/dir/file", "../x?y=1#f");
        assertResolveMatchesJava("http://example.com/", "a/b/./c/../d");
    }

    private static void assertResolveMatchesJava(String base, String relative) throws URISyntaxException {
        var customBase = DEFAULT_URI_FACTORY.create(base);
        var customRel = DEFAULT_URI_FACTORY.create(relative); // relative ref: scheme/authority null is OK for this class
        var customResolved = customBase.resolve(customRel).toString();

        var javaResolved = new java.net.URI(base).resolve(relative).toString();
        assertEquals(javaResolved, customResolved, "resolve(" + base + ", " + relative + ")");
    }

    @Test
    void acceptsCurlyBracesInPathWhereJavaNetURIRejects() throws Exception {
        String s = "http://example.com/{id}/x";

        // java.net.URI rejects '{' and '}' in paths by default
        assertThrows(URISyntaxException.class, () -> new java.net.URI(s));

        // custom URI is intended to accept '{' in paths
        assertDoesNotThrow(() -> ALLOW_ILLEGAL_CHARACTERS_URI_FACTORY.create(s));
        assertEquals(s, ALLOW_ILLEGAL_CHARACTERS_URI_FACTORY.create(s).toString());
        assertEquals("/{id}/x", ALLOW_ILLEGAL_CHARACTERS_URI_FACTORY.create(s).getRawPath());
    }

    @Test
    void knownDifference_URLDecoderTreatsPlusAsSpace() throws Exception {
        // java.net.URI decodes percent-escapes but does NOT treat '+' as space.
        // This custom URI uses URLDecoder, which DOES treat '+' as space.
        String s = "http://example.com/p?q=a+b";

        var j = new java.net.URI(s);
        var custom = DEFAULT_URI_FACTORY.create(s);

        assertEquals("q=a+b", j.getQuery(), "java query keeps '+'");
        assertEquals("q=a b", custom.getQuery(), "custom query turns '+' into space (URLDecoder)");
    }

    private static String expectedPathWithQueryFromJava(java.net.URI j) {
        var rawPath = j.getRawPath();
        if (rawPath == null || rawPath.isBlank()) rawPath = "/";
        var rawQuery = j.getRawQuery();
        return rawQuery == null ? rawPath : rawPath + "?" + rawQuery;
    }
}
