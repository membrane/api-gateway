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

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.net.URI;
import java.util.function.*;

import static com.predic8.membrane.core.util.URIUtil.*;
import static java.util.Optional.*;
import static org.junit.jupiter.api.Assertions.*;

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
    static Function<String,String> converter;

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
    void winColon()   {
        assertEquals("C:\\foo", converter.apply("file://C:/foo"));
    }

    @Test
    void winSlash()   {
        assertEquals("C:\\foo", converter.apply("file://C/foo"));
    }

    @Test
    void winSlashThree()   {
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
    void normalizeSingleDot() {
        assertEquals("", URIUtil.normalizeSingleDot(""));
        assertEquals("foo", URIUtil.normalizeSingleDot("foo"));
        assertEquals("/", URIUtil.normalizeSingleDot("/./"));
        assertEquals("a/b", URIUtil.normalizeSingleDot("a/./b"));
        assertEquals("a/b/c/", URIUtil.normalizeSingleDot("a/./b/./c/./"));
        assertEquals("a/b/c/d", URIUtil.normalizeSingleDot("a/./b/./c/./d"));

        // ?
        assertEquals("a?c/./d", URIUtil.normalizeSingleDot("a?c/./d"));
        assertEquals("a/b?c/./d", URIUtil.normalizeSingleDot("a/./b?c/./d"));
        assertEquals("a/.b?c/./d", URIUtil.normalizeSingleDot("a/.b?c/./d"));
        assertEquals("a/x/b?c/./d", URIUtil.normalizeSingleDot("a/x/b?c/./d"));
    }

    @Test
    void toFileURIStringTest() throws URISyntaxException {
        assertEquals("file:/swig/jig", toFileURIString(new File("/swig/jig")));
        assertEquals("file:/jag%20sag/runt", toFileURIString(new File("/jag sag/runt")));
    }

    @Test
    void toFileURIStringSpaceTest() throws URISyntaxException {
        assertEquals("file:/chip%20clip", toFileURIString(new File("/chip clip")));
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
}