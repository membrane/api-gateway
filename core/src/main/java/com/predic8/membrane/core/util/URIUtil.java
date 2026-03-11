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

import org.jetbrains.annotations.*;

import java.net.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Optional.*;

public class URIUtil {

    private static final Pattern driveLetterPattern = Pattern.compile("^(\\w)[/:|].*");
    private static final Pattern URI_SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    /**
     *
     * @param path Filepath like /foo/boo
     * @return
     * @throws URISyntaxException
     */
    public static java.net.URI convertPath2FileURI(String path) throws URISyntaxException {
        return new URI(addFilePrefix(encodePathCharactersForUri(path)));
    }

    public static String encodePathCharactersForUri(String s) {
        return s.replaceAll(" ", "%20").replace("\\", "/");
    }

    private static @NotNull String addFilePrefix(String path) {
        if (!path.startsWith("file:"))
            path = "file:" + path;
        if (path.charAt(5) != '/')
            path = "file:/" + path.substring(5);
        return path;
    }

    public static String convertPath2FilePathString(String path) {
        return encodePathCharactersForUri(addFilePrefix(path));
    }

    /**
     * Removes file protocol from uri
     *
     * @param uri path that can contain file protocol
     * @return path without the file protocol
     */
    public static String pathFromFileURI(URI uri) {
        return pathFromFileURI(uri.getPath());
    }

    /**
     * Removes file protocol from uri
     *
     * @param uri path that can contain file protocol
     * @return path without the file protocol
     */
    public static String pathFromFileURI(String uri) {
        return decode(processDecodedPart(stripFilePrefix(uri)), UTF_8);
    }

    private static String processDecodedPart(String path) {
        if (path.charAt(0) != '/')
            return path;
        String p = removeLocalhost(removeLeadingSlashes(path));
        return getPossibleDriveLetter(p).map(driveLetter -> "%s:\\%s".formatted(driveLetter, slashToBackslash(removeDriveLetterAndSlash(p)))).orElseGet(() -> "/" + p);

    }

    static String removeDriveLetterAndSlash(String path) {
        return path.replaceFirst("\\w[:?|/]/*", "");
    }

    static String slashToBackslash(String path) {
        return path.replace('/', '\\');
    }

    static String removeLocalhost(String s) {
        if (s.startsWith("localhost"))
            return s.substring(10);
        return s;
    }

    static String removeLeadingSlashes(String s) {
        return s.replaceAll("^/*", "");
    }

    static Optional<String> getPossibleDriveLetter(String p) {
        var m = driveLetterPattern.matcher(p);
        if (m.matches()) {
            return Optional.of(m.group(1));
        }
        return empty();
    }

    private static String stripFilePrefix(String uri) {
        if (!uri.startsWith("file:"))
            return uri;
        return uri.substring(5); // Remove "file:"
    }

    public static String normalizeSingleDot(String uri) {
        if (!uri.contains("/./"))
            return uri;

        StringBuilder sb = new StringBuilder(uri.length());
        for (int i = 0; i < uri.length(); i++) {
            int c = uri.codePointAt(i);
            switch (c) {
                case '?':
                    sb.append(uri.substring(i));
                    return sb.toString();
                case '/':
                    sb.appendCodePoint(c);
                    while (i < uri.length() - 2 && uri.codePointAt(i + 1) == '.' && uri.codePointAt(i + 2) == '/')
                        i += 2;
                    break;
                default:
                    sb.appendCodePoint(c);
            }
        }
        return sb.toString();
    }

    /**
     * Normalizes the given path or URI. This method handles various formats of paths,
     * including filesystem paths, URIs, and paths with potential Windows drive letters.
     * The normalization involves resolving relative components, such as "." or "..",
     * and ensuring the path conforms to a standardized format.
     *
     * @param location the path or URI string to normalize. It must not be null or empty.
     * @return the normalized path or URI as a string.
     * @throws IllegalArgumentException if the input location is null or empty.
     */
    public static String normalize(String location) {
        if (location == null || location.isEmpty())
            throw new IllegalArgumentException("location must not be null or empty");

        // Windows drive letter path (e.g., C:\foo or C:/foo)
        if (location.length() >= 2
            && Character.isLetter(location.charAt(0))
            && location.charAt(1) == ':') {
            return normalizeInternal(location);
        }

        // already absolute URI
        if (URI_SCHEME_PATTERN.matcher(location).matches()) {
            return URI.create(location).normalize().toString();
        }

        // ? (Query String) or # (Fragment) are hints of URLs, not filesystem paths
        if (location.contains("?") || location.contains("#") || location.startsWith("//")) {
            return URI.create(location).normalize().toString();
        }

        // filesystem path
        return normalizeInternal(location);
    }

    private static @NotNull String normalizeInternal(String location) {
        return Path.of(location).toAbsolutePath().normalize().toString();
    }

}