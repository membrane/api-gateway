/* Copyright 2024 predic8 GmbH, www.predic8.com

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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.function.Function;

import static com.predic8.membrane.core.util.URIUtil.*;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

/**
 * Unfortunately the file: protocol is
 * See
 * - <a href="https://stackoverflow.com/questions/7857416/file-uri-scheme-and-relative-files">File Uri Scheme and Relative Files</a>
 * - <a href="https://en.wikipedia.org/wiki/File_URI_scheme">File URI scheme</a>
 */
@SuppressWarnings("CommentedOutCode")
public class URIUtilTest {

    /**
     * Used to keep the tests and to try different URI or URIUtils implementations
     */
    static Function<String, String> converter;

    @BeforeAll
    static void setup() {
        converter = s -> {
            try {
                //  Keep comment to test other implementations
                //  return new URI(Paths.get(s).normalize().getFileName().toString()).getPath();
                //  return new URIFactory().create(s).getPath();
                return pathFromFileURI(s);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Test
    void plain() {
        assertEquals("foo", converter.apply("foo"));
    }

    @Test
    void onlyProtocol() {
        assertEquals("foo", converter.apply("file:foo"));
    }

    @Test
    void oneSlash() {
        assertEquals("/foo", converter.apply("file:/foo"));
    }

    /**
     * Two slashes are officially not allowed but used often
     */
    @Test
    void twoSlash() {
        assertEquals("/foo", converter.apply("file://foo"));
    }

    @Test
    void threeSlash() {
        assertEquals("/foo", converter.apply("file:///foo"));
    }

    @Test
    void localhost() {
        assertEquals("/foo", converter.apply("file://localhost/foo"));
    }

    @Test
    void winColon() {
        assertEquals("C:\\foo", converter.apply("file://C:/foo"));
    }

    @Test
    void winSlash() {
        assertEquals("C:\\foo", converter.apply("file://C/foo"));
    }

    @Test
    void winSlashThree() {
        assertEquals("C:\\foo", pathFromFileURI("file:///C/foo"));
    }

    // file://localhost/c:/WINDOWS/clock.avi

    /**
     * <a href="https://en.wikipedia.org/wiki/File_URI_scheme">File URI scheme</a>
     */
    @Test
    void casesFromWikipedia() {
        assertEquals("/etc/fstab", pathFromFileURI("file://localhost/etc/fstab"));
        assertEquals("/etc/fstab", pathFromFileURI("file:///etc/fstab"));
        assertEquals("/etc/fstab", pathFromFileURI("file:/etc/fstab"));

        assertEquals("c:\\WINDOWS\\clock.avi", pathFromFileURI("file://localhost/c:/WINDOWS/clock.avi"));
        assertEquals("c:\\WINDOWS\\clock.avi", pathFromFileURI("file:///c:/WINDOWS/clock.avi"));

        assertEquals("/path/to/the file.txt", pathFromFileURI("file://localhost/path/to/the%20file.txt"));

        assertEquals("c:\\path\\to\\the file.txt", pathFromFileURI("file:///c:/path/to/the%20file.txt"));

    }

    @Test
    void leadingSlashes() {
        assertEquals("foo", removeLeadingSlashes("foo"));
        assertEquals("foo", removeLeadingSlashes("/foo"));
        assertEquals("foo", removeLeadingSlashes("//foo"));
        assertEquals("foo", removeLeadingSlashes("///foo"));
        assertEquals("a/b", removeLeadingSlashes("/a/b"));
    }

    @Test
    void getDriveLetters() {
        assertEquals(empty(), getPossibleDriveLetter("foo"));
        assertEquals(of("c"), getPossibleDriveLetter("c/foo"));
        assertEquals(of("c"), getPossibleDriveLetter("c:foo"));
        assertEquals(of("c"), getPossibleDriveLetter("c|foo"));
        assertEquals(of("c"), getPossibleDriveLetter("c|"));
        assertEquals(empty(), getPossibleDriveLetter(""));
    }

    @Test
    void removeLocalhost() {
        assertEquals("foo", URIUtil.removeLocalhost("foo"));
        assertEquals("foo", URIUtil.removeLocalhost("localhost/foo"));
    }

    @Test
    void backslashesWindows() {
        assertEquals("a", URIUtil.slashToBackslash("a"));
        assertEquals("\\", URIUtil.slashToBackslash("/"));
        assertEquals("\\\\", URIUtil.slashToBackslash("//"));
        assertEquals("a\\b\\c", URIUtil.slashToBackslash("a/b/c"));
    }

    @Test
    void driveLetterAndSlash() {
        assertEquals("b", removeDriveLetterAndSlash("a/b"));
        assertEquals("foo", removeDriveLetterAndSlash("C/foo"));
        assertEquals("b", removeDriveLetterAndSlash("a:/b"));
        assertEquals("b", removeDriveLetterAndSlash("a|b"));
        assertEquals("b", removeDriveLetterAndSlash("a|/b"));
    }

    @Test
    void decodeUnreservedTest() {
        assertEquals("/a/../b", decodeUnreserved("/a/%2e%2e/b"));
        assertEquals("/a/../b", decodeUnreserved("/a/%2E%2e/b"));
        assertEquals("/a/../b", decodeUnreserved("/a/.%2e/b"));
        assertEquals("/a/./b", decodeUnreserved("/a/%2e/b"));
        assertEquals("/a/...", decodeUnreserved("/a/%2e%2e%2e"));
        assertEquals("/a/file.txt", decodeUnreserved("/a/file%2etxt"));
        assertEquals("/admin", decodeUnreserved("/%61dmin"));
        assertEquals("/Admin", decodeUnreserved("/%41dmin"));
        assertEquals("/~-_09", decodeUnreserved("/%7E%2d%5f%30%39"));

        // Reserved and other characters stay encoded as they are
        assertEquals("/a%2f..%2Fb", decodeUnreserved("/a%2f%2e%2e%2Fb"));
        assertEquals("/a%3bb%25c%20d%3F", decodeUnreserved("/a%3bb%25c%20d%3F"));
        assertEquals("/%C3%A4", decodeUnreserved("/%C3%A4"));

        // %25 is not decoded, so a double-encoded character stays double-encoded
        assertEquals("/%2561", decodeUnreserved("/%2561"));

        assertEquals("/", decodeUnreserved("/"));
    }

    @Test
    void decodeUnreservedRejectsMalformedEscapes() {
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/a%zz"));
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/a%6"));
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/a%"));
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/a%g1"));
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/a%1g"));

        // Decoding the characters behind a malformed escape would create a new escape like %61 or %2E
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/%6%31dmin"));
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/%%361dmin"));
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/x/%2%45%2%45/admin"));

        // Only ASCII hex digits are accepted, Character.digit() would also accept e.g. fullwidth digits
        assertThrows(IllegalArgumentException.class, () -> decodeUnreserved("/%６１dmin"));
    }

    @Test
    void containsAmbiguousPathParametersTest() {
        // Dot-segments with parameters
        assertTrue(containsAmbiguousPathParameters("/x/..;/admin"));
        assertTrue(containsAmbiguousPathParameters("/x/..;a=b;c=d/admin"));
        assertTrue(containsAmbiguousPathParameters("/x/.;/admin"));
        assertTrue(containsAmbiguousPathParameters("/x/..;"));
        assertTrue(containsAmbiguousPathParameters("/..;/admin"));

        // Empty segments with parameters before another segment
        assertTrue(containsAmbiguousPathParameters("/;x/admin"));
        assertTrue(containsAmbiguousPathParameters("/;/admin"));
        assertTrue(containsAmbiguousPathParameters("/api/;x/admin"));

        assertFalse(containsAmbiguousPathParameters("/admin"));
        assertFalse(containsAmbiguousPathParameters("/admin;jsessionid=1"));
        assertFalse(containsAmbiguousPathParameters("/app/;jsessionid=1"));
        assertFalse(containsAmbiguousPathParameters("/;jsessionid=1"));
        assertFalse(containsAmbiguousPathParameters("/x/a;b=c/y"));
        assertFalse(containsAmbiguousPathParameters("/x/...;/y"));
        assertFalse(containsAmbiguousPathParameters("/x/..a;/y"));
        assertFalse(containsAmbiguousPathParameters("/x/..%3b/y")); // An encoded ';' does not start parameters
        assertFalse(containsAmbiguousPathParameters("/x?p=/..;/admin"));
        assertFalse(containsAmbiguousPathParameters("http://h/..;/admin"));
    }

    @Test
    void removePathParametersTest() {
        assertEquals("/api/admin", removePathParameters("/api;x/admin"));
        assertEquals("/api/admin", removePathParameters("/api;a=b;c=d/admin;e"));
        assertEquals("/app/", removePathParameters("/app/;jsessionid=1"));
        assertEquals("/api/admin?q=a;b", removePathParameters("/api;x/admin?q=a;b"));
        assertEquals("/a%3bb", removePathParameters("/a%3bb"));
        assertEquals("/api/admin", removePathParameters("/api/admin"));
        assertEquals("*", removePathParameters("*"));
        assertEquals("http://h/a;b", removePathParameters("http://h/a;b"));
    }

    @Test
    void mergeSlashesTest() {
        assertEquals("/admin", mergeSlashes("//admin"));
        assertEquals("/api/admin/", mergeSlashes("//api///admin//"));
        assertEquals("/admin?x=//y", mergeSlashes("//admin?x=//y"));
        assertEquals("/api/admin", mergeSlashes("/api/admin"));
        assertEquals("*", mergeSlashes("*"));
        assertEquals("http://h//a", mergeSlashes("http://h//a"));
    }

    @Test
    void toRoutingPathTest() {
        assertEquals("/api/admin?q=a;b", toRoutingPath("//api;jsessionid=1//admin?q=a;b"));
        assertEquals("/admin", toRoutingPath("/;x/admin"));
        assertEquals("/app/", toRoutingPath("/app/;jsessionid=1"));
        assertEquals("/api/admin", toRoutingPath("/api/admin"));
    }

    @Test
    void normalizeRequestTargetTest() {
        assertEquals("/", normalizeRequestTarget("/"));
        assertEquals("/", normalizeRequestTarget("/./"));
        assertEquals("/a/b/c/", normalizeRequestTarget("/a/./b/./c/./"));
        assertEquals("/b", normalizeRequestTarget("/a/../b"));
        assertEquals("/b", normalizeRequestTarget("/../../b"));
        assertEquals("/a/", normalizeRequestTarget("/a/b/.."));
        assertEquals("/b", normalizeRequestTarget("/a/%2e%2E/b"));
        assertEquals("/b", normalizeRequestTarget("/a/.%2e/b"));
        assertEquals("/a/b", normalizeRequestTarget("/a/%2e/b"));
        assertEquals("/a/.b/..c", normalizeRequestTarget("/a/.b/..c"));
        assertEquals("/a/file.txt", normalizeRequestTarget("/a/file%2etxt"));
        assertEquals("/admin", normalizeRequestTarget("/%61dmin"));
        assertEquals("/admin", normalizeRequestTarget("/x/%2e%2e/%61dmin"));
        assertEquals("/a%2f..%2fb", normalizeRequestTarget("/a%2f..%2fb"));

        // query is left alone
        assertEquals("/b?c/./d/../e", normalizeRequestTarget("/a/../b?c/./d/../e"));
        assertEquals("/a?c/../d", normalizeRequestTarget("/a?c/../d"));

        // a malformed escape in the path is rejected, the query is not decoded and so not checked
        assertThrows(IllegalArgumentException.class, () -> normalizeRequestTarget("/%6%31dmin?q=1"));
        assertEquals("/admin?q=%6", normalizeRequestTarget("/%61dmin?q=%6"));

        // not origin-form
        assertEquals("*", normalizeRequestTarget("*"));
        assertEquals("example.com:443", normalizeRequestTarget("example.com:443"));
        assertEquals("internal://a/../b", normalizeRequestTarget("internal://a/../b"));
        assertEquals("http://h/../b", normalizeRequestTarget("http://h/../b")); // .. must not remove the host
    }

    @Test
    void toFileURIStringTest() throws URISyntaxException {
        assertEquals(wl("file:/" + currentDrive() + "/swig/jig", "file:/swig/jig"), FileUtil.toFileURIString(new File("/swig/jig")));
        assertEquals(wl("file:/" + currentDrive() + "/jag%20sag/runt", "file:/jag%20sag/runt"), FileUtil.toFileURIString(new File("/jag sag/runt")));
    }

    String wl(String windows, String linux) {
        if (OSUtil.isWindows())
            return windows;
        return linux;
    }

    /**
     * A leading "/" in a File resolves against the JVM's current drive, which isn't
     * necessarily C: (e.g. CI runners may check out onto D:). Only meaningful on Windows;
     * {@code wl()} evaluates both arguments eagerly, so this must not throw on other OSes.
     */
    String currentDrive() {
        if (!OSUtil.isWindows())
            return "";
        return new File("/").getAbsolutePath().substring(0, 2);
    }

    @Test
    void toFileURIStringSpaceTest() throws URISyntaxException {
        assertEquals(wl(
                "file:/" + currentDrive() + "/chip%20clip",
                "file:/chip%20clip"
        ), FileUtil.toFileURIString(new File("/chip clip")));
    }

    @Test
    void convertPath2FileURITest() throws URISyntaxException {
        assertEquals(new URI("file:///foo"), convertPath2FileURI("/foo"));
        assertEquals(new URI("file:/foo/boo"), convertPath2FileURI("\\foo\\boo"));
        assertEquals(new URI("file:/foo"), convertPath2FileURI("file:/foo"));
        assertEquals(new URI("file://foo"), convertPath2FileURI("file://foo"));
        assertEquals(new URI("file:///foo"), convertPath2FileURI("file:///foo"));
        assertEquals(new URI("file:/c:/foo/boo"), convertPath2FileURI("c:\\foo\\boo"));
    }

    @Nested
    class normalize {
        @Test
        void throwsOnNull() {
            assertThrows(IllegalArgumentException.class, () -> getNormalizedAbsolutePathOrUri(null));
        }


        @Test
        void throwsOnEmpty() {
            assertThrows(IllegalArgumentException.class, () -> getNormalizedAbsolutePathOrUri(""));
        }

        @Test
        void normalizesRelativeFilesystemPath() {
            var input = "foo/../bar/test.txt";
            var expected = Path.of(input).toAbsolutePath().normalize().toString();
            assertEquals(expected, getNormalizedAbsolutePathOrUri(input));
        }

        @Test
        void normalizesAbsoluteFilesystemPath() {
            var input = Path.of("foo", "..", "bar").toAbsolutePath().toString();
            var expected = Path.of(input).toAbsolutePath().normalize().toString();
            assertEquals(expected, getNormalizedAbsolutePathOrUri(input));
        }

        @Test
        void normalizesFileUri() {
            assertEquals("file:/test.xml", getNormalizedAbsolutePathOrUri("file:///tmp/../test.xml"));
        }

        @Test
        void normalizesHttpUri() {
            assertEquals("http://example.com/b/wsdl.xsd", getNormalizedAbsolutePathOrUri("http://example.com/a/../b/wsdl.xsd"));
        }

        @Test
        void normalizesHttpUriWithQuery() {
            assertEquals("http://example.com/b/wsdl.xsd?a=a/../b", getNormalizedAbsolutePathOrUri("http://example.com/a/../b/wsdl.xsd?a=a/../b"));
        }


        @Test
        void classpathUri() {
            // The '..' within the path portion normalizes correctly.
            assertEquals("classpath://authority/xsd/test.xsd", getNormalizedAbsolutePathOrUri("classpath://authority/schema/../xsd/test.xsd"));
        }

        @Test
        void keepsClasspathUri() {
            // The '..' at the start of the path (after authority) cannot traverse above the authority, so it's preserved.
            assertEquals("classpath://authority/../xsd/test.xsd", getNormalizedAbsolutePathOrUri("classpath://authority/../xsd/test.xsd"));
        }

        @Test
        void normalizesUnixLikePath() {
            var input = "/tmp/../var/data.xsd";
            var expected = Path.of(input).toAbsolutePath().normalize().toString();
            assertEquals(expected, getNormalizedAbsolutePathOrUri(input));
        }

        @Test
        @EnabledOnOs(WINDOWS)
        void handlesWindowsDriveLetterPath() {
            var input = "C:\\temp\\..\\data\\test.xsd";
            var expected = Path.of(input).toAbsolutePath().normalize().toString();
            assertEquals(expected, getNormalizedAbsolutePathOrUri(input));
        }

        @Test
        @EnabledOnOs(WINDOWS)
        void handlesWindowsDriveRelativePath() {
            var input = "C:temp\\..\\data\\test.xsd";
            var expected = Path.of(input).toAbsolutePath().normalize().toString();
            assertEquals(expected, getNormalizedAbsolutePathOrUri(input));
        }

        @Test
        void keepsRelativeUriWithQuery() {
            assertEquals("schema.xsd?version=1", getNormalizedAbsolutePathOrUri("schema.xsd?version=1")
            );
        }

        @Test
        void keepsRelativeUriWithFragment() {
            assertEquals("../types.xsd#frag", getNormalizedAbsolutePathOrUri("../types.xsd#frag")
            );
        }

        @Test
        void keepsRelativeUriWithQueryAndFragment() {
            assertEquals("../types.xsd?version=1#frag", getNormalizedAbsolutePathOrUri("../types.xsd?version=1#frag")
            );
        }

        @Test
        void keepsSchemeRelativeReference() {
            assertEquals("//example.com/schema.xsd?version=1", getNormalizedAbsolutePathOrUri("//example.com/schema.xsd?version=1"));
        }
    }
}