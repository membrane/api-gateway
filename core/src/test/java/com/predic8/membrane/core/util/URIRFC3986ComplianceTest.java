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

import static com.predic8.membrane.core.util.URI.removeDotSegments;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests URI resolution against the examples from RFC 3986, Section 5.4.
 *
 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986</a>
 */
class URIRFC3986ComplianceTest {

    /**
     * Base URI from RFC 3986, Section 5.4:
     * <pre>http://a/b/c/d;p?q</pre>
     */
    private static final String BASE = "http://a/b/c/d;p?q";

    private com.predic8.membrane.core.util.URI base;

    @BeforeEach
    void setUp() throws URISyntaxException {
        base = new URI(BASE, true);
    }

    // ----------------------------------------------------------------
    // Section 5.4.1 - Normal Examples
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("RFC 3986 Section 5.4.1 - Normal Examples")
    class NormalExamples {

        @Test
        @DisplayName("g:h -> g:h (different scheme)")
        void differentScheme() throws URISyntaxException {
            assertResolves("g:h", "g:h");
        }

        @Test
        @DisplayName("g -> http://a/b/c/g")
        void relativePath() throws URISyntaxException {
            assertResolves("g", "http://a/b/c/g");
        }

        @Test
        @DisplayName("./g -> http://a/b/c/g")
        void dotSlashRelative() throws URISyntaxException {
            assertResolves("./g", "http://a/b/c/g");
        }

        @Test
        @DisplayName("g/ -> http://a/b/c/g/")
        void relativeWithTrailingSlash() throws URISyntaxException {
            assertResolves("g/", "http://a/b/c/g/");
        }

        @Test
        @DisplayName("/g -> http://a/g")
        void absolutePath() throws URISyntaxException {
            assertResolves("/g", "http://a/g");
        }

        @Test
        @DisplayName("//g -> http://g")
        void networkPathReference() throws URISyntaxException {
            assertResolves("//g", "http://g");
        }

        @Test
        @DisplayName("?y -> http://a/b/c/d;p?y")
        void queryOnly() throws URISyntaxException {
            assertResolves("?y", "http://a/b/c/d;p?y");
        }

        @Test
        @DisplayName("g?y -> http://a/b/c/g?y")
        void relativeWithQuery() throws URISyntaxException {
            assertResolves("g?y", "http://a/b/c/g?y");
        }

        @Test
        @DisplayName("#s -> http://a/b/c/d;p?q#s")
        void fragmentOnly() throws URISyntaxException {
            assertResolves("#s", "http://a/b/c/d;p?q#s");
        }

        @Test
        @DisplayName("g#s -> http://a/b/c/g#s")
        void relativeWithFragment() throws URISyntaxException {
            assertResolves("g#s", "http://a/b/c/g#s");
        }

        @Test
        @DisplayName("g?y#s -> http://a/b/c/g?y#s")
        void relativeWithQueryAndFragment() throws URISyntaxException {
            assertResolves("g?y#s", "http://a/b/c/g?y#s");
        }

        @Test
        @DisplayName(";x -> http://a/b/c/;x")
        void semicolonRelative() throws URISyntaxException {
            assertResolves(";x", "http://a/b/c/;x");
        }

        @Test
        @DisplayName("g;x -> http://a/b/c/g;x")
        void relativeWithParams() throws URISyntaxException {
            assertResolves("g;x", "http://a/b/c/g;x");
        }

        @Test
        @DisplayName("g;x?y#s -> http://a/b/c/g;x?y#s")
        void relativeWithParamsQueryFragment() throws URISyntaxException {
            assertResolves("g;x?y#s", "http://a/b/c/g;x?y#s");
        }

        @Test
        @DisplayName("\"\" -> http://a/b/c/d;p?q (empty reference)")
        void emptyReference() throws URISyntaxException {
            assertResolves("", "http://a/b/c/d;p?q");
        }

        @Test
        @DisplayName(". -> http://a/b/c/")
        void singleDot() throws URISyntaxException {
            assertResolves(".", "http://a/b/c/");
        }

        @Test
        @DisplayName("./ -> http://a/b/c/")
        void dotSlash() throws URISyntaxException {
            assertResolves("./", "http://a/b/c/");
        }

        @Test
        @DisplayName(".. -> http://a/b/")
        void doubleDot() throws URISyntaxException {
            assertResolves("..", "http://a/b/");
        }

        @Test
        @DisplayName("../ -> http://a/b/")
        void doubleDotSlash() throws URISyntaxException {
            assertResolves("../", "http://a/b/");
        }

        @Test
        @DisplayName("../g -> http://a/b/g")
        void parentRelative() throws URISyntaxException {
            assertResolves("../g", "http://a/b/g");
        }

        @Test
        @DisplayName("../.. -> http://a/")
        void twoLevelsUp() throws URISyntaxException {
            assertResolves("../..", "http://a/");
        }

        @Test
        @DisplayName("../../ -> http://a/")
        void twoLevelsUpSlash() throws URISyntaxException {
            assertResolves("../../", "http://a/");
        }

        @Test
        @DisplayName("../../g -> http://a/g")
        void twoLevelsUpRelative() throws URISyntaxException {
            assertResolves("../../g", "http://a/g");
        }
    }

    // ----------------------------------------------------------------
    // Section 5.4.2 - Abnormal Examples
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("RFC 3986 Section 5.4.2 - Abnormal Examples")
    class AbnormalExamples {

        @Test
        @DisplayName("../../../g -> http://a/g (above root)")
        void threeLevelsUp() throws URISyntaxException {
            assertResolves("../../../g", "http://a/g");
        }

        @Test
        @DisplayName("../../../../g -> http://a/g (far above root)")
        void fourLevelsUp() throws URISyntaxException {
            assertResolves("../../../../g", "http://a/g");
        }

        @Test
        @DisplayName("/./g -> http://a/g")
        void absoluteDotSegment() throws URISyntaxException {
            assertResolves("/./g", "http://a/g");
        }

        @Test
        @DisplayName("/../g -> http://a/g")
        void absoluteDoubleDotSegment() throws URISyntaxException {
            assertResolves("/../g", "http://a/g");
        }

        @Test
        @DisplayName("g. -> http://a/b/c/g. (not a dot segment)")
        void trailingDot() throws URISyntaxException {
            assertResolves("g.", "http://a/b/c/g.");
        }

        @Test
        @DisplayName(".g -> http://a/b/c/.g (not a dot segment)")
        void leadingDot() throws URISyntaxException {
            assertResolves(".g", "http://a/b/c/.g");
        }

        @Test
        @DisplayName("g.. -> http://a/b/c/g.. (not a dot segment)")
        void trailingDoubleDot() throws URISyntaxException {
            assertResolves("g..", "http://a/b/c/g..");
        }

        @Test
        @DisplayName("..g -> http://a/b/c/..g (not a dot segment)")
        void leadingDoubleDot() throws URISyntaxException {
            assertResolves("..g", "http://a/b/c/..g");
        }

        @Test
        @DisplayName("./../g -> http://a/b/g")
        void mixedDotSegments() throws URISyntaxException {
            assertResolves("./../g", "http://a/b/g");
        }

        @Test
        @DisplayName("./g/. -> http://a/b/c/g/")
        void trailingDotInPath() throws URISyntaxException {
            assertResolves("./g/.", "http://a/b/c/g/");
        }

        @Test
        @DisplayName("g/./h -> http://a/b/c/g/h")
        void dotInMiddle() throws URISyntaxException {
            assertResolves("g/./h", "http://a/b/c/g/h");
        }

        @Test
        @DisplayName("g/../h -> http://a/b/c/h")
        void doubleDotInMiddle() throws URISyntaxException {
            assertResolves("g/../h", "http://a/b/c/h");
        }

        @Test
        @DisplayName("g;x=1/./y -> http://a/b/c/g;x=1/y")
        void paramsWithDot() throws URISyntaxException {
            assertResolves("g;x=1/./y", "http://a/b/c/g;x=1/y");
        }

        @Test
        @DisplayName("g;x=1/../y -> http://a/b/c/y")
        void paramsWithDoubleDot() throws URISyntaxException {
            assertResolves("g;x=1/../y", "http://a/b/c/y");
        }

        @Test
        @DisplayName("g?y/./x -> http://a/b/c/g?y/./x (dots in query are literal)")
        void dotsInQuery() throws URISyntaxException {
            assertResolves("g?y/./x", "http://a/b/c/g?y/./x");
        }

        @Test
        @DisplayName("g?y/../x -> http://a/b/c/g?y/../x (dots in query are literal)")
        void doubleDotsInQuery() throws URISyntaxException {
            assertResolves("g?y/../x", "http://a/b/c/g?y/../x");
        }

        @Test
        @DisplayName("g#s/./x -> http://a/b/c/g#s/./x (dots in fragment are literal)")
        void dotsInFragment() throws URISyntaxException {
            assertResolves("g#s/./x", "http://a/b/c/g#s/./x");
        }

        @Test
        @DisplayName("g#s/../x -> http://a/b/c/g#s/../x (dots in fragment are literal)")
        void doubleDotsInFragment() throws URISyntaxException {
            assertResolves("g#s/../x", "http://a/b/c/g#s/../x");
        }

        @Test
        @DisplayName("http:g -> http:g (strict interpretation)")
        void sameSchemeStrict() throws URISyntaxException {
            // RFC 3986 strict interpretation: "http:g" is a URI with scheme "http"
            // and path "g" -> returned as-is
            assertResolves("http:g", "http:g");
        }
    }

    // ----------------------------------------------------------------
    // Section 5.2.4 - removeDotSegments
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("RFC 3986 Section 5.2.4 - removeDotSegments")
    class RemoveDotSegmentsTests {

        @Test
        @DisplayName("null input returns null")
        void nullInput() {
            assertNull(removeDotSegments(null));
        }

        @Test
        @DisplayName("empty input returns empty")
        void emptyInput() {
            assertEquals("", removeDotSegments(""));
        }

        @Test
        @DisplayName("/a/b/c/./../../g -> /a/g (RFC 3986 Section 5.2.4 example)")
        void rfcExample() {
            assertEquals("/a/g", removeDotSegments("/a/b/c/./../../g"));
        }

        @Test
        @DisplayName("mid/content=5/../6 -> mid/6 (RFC 3986 Section 5.2.4 example)")
        void rfcExampleRelative() {
            assertEquals("mid/6", removeDotSegments("mid/content=5/../6"));
        }

        @Test
        void leadingDotDotSlash() {
            assertEquals("a", removeDotSegments("../a"));
        }

        @Test
        void leadingDotSlash() {
            assertEquals("a", removeDotSegments("./a"));
        }

        @Test
        void midDotSlash() {
            assertEquals("a/b", removeDotSegments("a/./b"));
        }

        @Test
        void midDotDotSlash() {
            assertEquals("/b", removeDotSegments("a/../b"));
        }

        @Test
        void chainedDotDot() {
            assertEquals("a/c", removeDotSegments("a/b/../c"));
        }

        @Test
        void aboveRoot() {
            assertEquals("/a", removeDotSegments("/../a"));
        }

        @Test
        void noDotsUnchanged() {
            assertEquals("/a/b/c", removeDotSegments("/a/b/c"));
        }

        @Test
        void trailingDot() {
            assertEquals("/a/", removeDotSegments("/a/."));
        }

        @Test
        void trailingDoubleDot() {
            assertEquals("/", removeDotSegments("/a/.."));
        }

        @Test
        void onlyDot() {
            assertEquals("", removeDotSegments("."));
        }

        @Test
        void onlyDoubleDot() {
            assertEquals("", removeDotSegments(".."));
        }

        @Test
        void deepNormalization() {
            assertEquals("/g", removeDotSegments("/a/b/c/../../../g"));
        }
    }

    // ----------------------------------------------------------------
    // Section 5.3 - Component Recomposition
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("RFC 3986 Section 5.3 - Component Recomposition")
    class RecompositionTests {

        @Test
        @DisplayName("Scheme is included with colon separator")
        void schemeIncluded() throws URISyntaxException {
            URI b = new URI("http://host", true);
            com.predic8.membrane.core.util.URI r = new URI("/path", true);
            String result = b.resolve(r).toString();
            assertTrue(result.startsWith("http:"));
        }

        @Test
        @DisplayName("Authority is prefixed with //")
        void authorityPrefixed() throws URISyntaxException {
            com.predic8.membrane.core.util.URI b = new URI("http://host", true);
            com.predic8.membrane.core.util.URI r = new com.predic8.membrane.core.util.URI("/path", true);
            String result = b.resolve(r).toString();
            assertTrue(result.contains("//host"));
        }

        @Test
        @DisplayName("Query is prefixed with ?")
        void queryPrefixed() throws URISyntaxException {
            com.predic8.membrane.core.util.URI b = new com.predic8.membrane.core.util.URI("http://host", true);
            URI r = new com.predic8.membrane.core.util.URI("/path?key=val", true);
            String result = b.resolve(r).toString();
            assertTrue(result.contains("?key=val"));
        }

        @Test
        @DisplayName("Fragment is prefixed with #")
        void fragmentPrefixed() throws URISyntaxException {
            com.predic8.membrane.core.util.URI b = new com.predic8.membrane.core.util.URI("http://host", true);
            com.predic8.membrane.core.util.URI r = new URI("/path#frag", true);
            String result = b.resolve(r).toString();
            assertTrue(result.contains("#frag"));
        }

        @Test
        @DisplayName("Full recomposition preserves all components")
        void fullRecomposition() throws URISyntaxException {
            URI b = new URI("http://host", true);
            com.predic8.membrane.core.util.URI r = new com.predic8.membrane.core.util.URI("/path?q=1#f", true);
            assertEquals("http://host/path?q=1#f", b.resolve(r).toString());
        }
    }

    // ----------------------------------------------------------------
    // Parameterized - all normal + abnormal in one go
    // ----------------------------------------------------------------

    @DisplayName("RFC 3986 Section 5.4 - Parameterized")
    @ParameterizedTest(name = "resolve(\"{0}\") = \"{1}\"")
    @MethodSource("rfc3986Section54Examples")
    void rfc3986ResolveExamples(String relative, String expected) throws URISyntaxException {
        assertResolves(relative, expected);
    }

    static Stream<Arguments> rfc3986Section54Examples() {
        return Stream.of(
                // Section 5.4.1 - Normal Examples
                Arguments.of("g:h", "g:h"),
                Arguments.of("g", "http://a/b/c/g"),
                Arguments.of("./g", "http://a/b/c/g"),
                Arguments.of("g/", "http://a/b/c/g/"),
                Arguments.of("/g", "http://a/g"),
                Arguments.of("//g", "http://g"),
                Arguments.of("?y", "http://a/b/c/d;p?y"),
                Arguments.of("g?y", "http://a/b/c/g?y"),
                Arguments.of("#s", "http://a/b/c/d;p?q#s"),
                Arguments.of("g#s", "http://a/b/c/g#s"),
                Arguments.of("g?y#s", "http://a/b/c/g?y#s"),
                Arguments.of(";x", "http://a/b/c/;x"),
                Arguments.of("g;x", "http://a/b/c/g;x"),
                Arguments.of("g;x?y#s", "http://a/b/c/g;x?y#s"),
                Arguments.of("", "http://a/b/c/d;p?q"),
                Arguments.of(".", "http://a/b/c/"),
                Arguments.of("./", "http://a/b/c/"),
                Arguments.of("..", "http://a/b/"),
                Arguments.of("../", "http://a/b/"),
                Arguments.of("../g", "http://a/b/g"),
                Arguments.of("../..", "http://a/"),
                Arguments.of("../../", "http://a/"),
                Arguments.of("../../g", "http://a/g"),

                // Section 5.4.2 - Abnormal Examples
                Arguments.of("../../../g", "http://a/g"),
                Arguments.of("../../../../g", "http://a/g"),
                Arguments.of("/./g", "http://a/g"),
                Arguments.of("/../g", "http://a/g"),
                Arguments.of("g.", "http://a/b/c/g."),
                Arguments.of(".g", "http://a/b/c/.g"),
                Arguments.of("g..", "http://a/b/c/g.."),
                Arguments.of("..g", "http://a/b/c/..g"),
                Arguments.of("./../g", "http://a/b/g"),
                Arguments.of("./g/.", "http://a/b/c/g/"),
                Arguments.of("g/./h", "http://a/b/c/g/h"),
                Arguments.of("g/../h", "http://a/b/c/h"),
                Arguments.of("g;x=1/./y", "http://a/b/c/g;x=1/y"),
                Arguments.of("g;x=1/../y", "http://a/b/c/y"),
                Arguments.of("g?y/./x", "http://a/b/c/g?y/./x"),
                Arguments.of("g?y/../x", "http://a/b/c/g?y/../x"),
                Arguments.of("g#s/./x", "http://a/b/c/g#s/./x"),
                Arguments.of("g#s/../x", "http://a/b/c/g#s/../x"),
                Arguments.of("http:g", "http:g")
        );
    }

    private void assertResolves(String relative, String expected) throws URISyntaxException {
        URI rel = new URI(relative, true);
        String actual = base.resolve(rel).toString();
        assertEquals(expected, actual,
                "Resolving \"%s\" against base <%s>".formatted(relative, BASE));
    }
}
