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

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.util.URI.removeDotSegments;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.joining;

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

    /**
     * Resolves the dot-segments "." and ".." (also percent-encoded as %2e, RFC 3986 2.3) in the path of an
     * origin-form request target, so that routing, the flow and the forwarded request all see the same path.
     * The query is left untouched. Other forms (absolute-form, authority-form, asterisk-form) are returned unchanged.
     */
    public static String removeDotSegmentsFromRequestTarget(String uri) {
        if (!uri.startsWith("/"))
            return uri;

        final var queryStart = uri.indexOf('?');
        final var path = queryStart == -1 ? uri : uri.substring(0, queryStart);
        if (!containsDotSegment(path))
            return uri;

        final var query = queryStart == -1 ? "" : uri.substring(queryStart);
        return removeDotSegments(decodeDotSegments(path)) + query;
    }

    /**
     * A dot-segment always starts directly after a slash, with a dot or an encoded dot.
     */
    private static boolean containsDotSegment(String path) {
        return path.contains("/.") || path.contains("/%2e") || path.contains("/%2E");
    }

    /**
     * Decodes percent-encoded dot-segments, e.g. "/a/%2e%2e/b" becomes "/a/../b", so that
     * URI.removeDotSegments() resolves them like plain ones. The split keeps empty segments,
     * so leading, trailing and double slashes are preserved.
     */
    static String decodeDotSegments(String path) {
        // Without a '%' there is nothing to decode. Spares plain "/a/../b" or "/.well-known/..." the split and join.
        if (path.indexOf('%') == -1)
            return path;

        return Arrays.stream(path.split("/", -1))
                .map(URIUtil::decodeDotSegment)
                .collect(joining("/"));
    }

    /**
     * Only segments consisting solely of (encoded) dots are decoded, e.g. "file%2etxt" stays as it is.
     */
    private static String decodeDotSegment(String segment) {
        return switch (segment.toLowerCase(Locale.ROOT)) {
            case ".", "%2e" -> ".";
            case "..", ".%2e", "%2e.", "%2e%2e" -> "..";
            default -> segment;
        };
    }

    /**
     * Normalizes the given path or URI and resolves it to an absolute path.
     * This method handles various formats of paths,
     * including filesystem paths, URIs, and paths with potential Windows drive letters.
     * The normalization involves resolving relative components, such as "." or "..",
     * and ensuring the path conforms to a standardized format.
     *
     * @param location the path or URI string to normalize.
     * @return the normalized absolute path or URI as a string.
     * @throws IllegalArgumentException if the input location is null, empty, or contains malformed URI syntax.
     */
    public static String getNormalizedAbsolutePathOrUri(String location) {
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