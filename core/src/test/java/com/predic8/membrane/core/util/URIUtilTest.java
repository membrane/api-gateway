package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

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

}