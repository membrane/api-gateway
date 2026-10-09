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
    private static final Pattern MULTIPLE_SLASHES = Pattern.compile("/{2,}");

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
     * Normalizes the path of an origin-form request target, so that routing, the flow and the forwarded
     * request all see the same path:
     * <ol>
     *     <li>Percent-encoded unreserved characters are decoded (RFC 3986 6.2.2.2), e.g. "/%61dmin" becomes "/admin"
     *     and "/a/%2e%2e/b" becomes "/a/../b". Reserved characters like %2F stay encoded.</li>
     *     <li>The dot-segments "." and ".." are resolved (RFC 3986 6.2.2.3).</li>
     * </ol>
     * The query is left untouched. Other forms (absolute-form, authority-form, asterisk-form) are returned unchanged.
     *
     * @throws IllegalArgumentException if the path contains a malformed percent-escape like "%6" or "%zz"
     */
    public static String normalizeRequestTarget(String uri) {
        if (!uri.startsWith("/"))
            return uri;

        final var queryStart = uri.indexOf('?');
        final var path = decodeUnreserved(queryStart == -1 ? uri : uri.substring(0, queryStart));
        final var query = queryStart == -1 ? "" : uri.substring(queryStart);
        if (!containsDotSegment(path))
            return path + query;

        return removeDotSegments(path) + query;
    }

    /**
     * Tells whether the path of an origin-form request target has path parameters (";...", RFC 3986 3.3) in one of
     * the suspicious sequences that Jakarta Servlet 6.0 (3.5.2) requires a container to reject with 400:
     * <ul>
     *     <li>a dot-segment with parameters, like "/x/..;/admin", which a servlet container resolves to "/admin"</li>
     *     <li>an empty segment with parameters other than the last segment, like "/;x/admin"</li>
     * </ul>
     * A trailing empty segment with parameters, like "/app/;jsessionid=1" from servlet URL rewriting, is fine.
     * Expects a target normalized by {@link #normalizeRequestTarget(String)}, so encoded dots are already decoded.
     */
    public static boolean containsAmbiguousPathParameters(String uri) {
        if (!uri.startsWith("/"))
            return false;

        final var queryStart = uri.indexOf('?');
        final var path = queryStart == -1 ? uri : uri.substring(0, queryStart);
        if (path.indexOf(';') == -1)
            return false;

        final var segments = path.split("/", -1);
        for (var i = 1; i < segments.length; i++) {
            final var parameterStart = segments[i].indexOf(';');
            if (parameterStart == -1)
                continue;

            final var name = segments[i].substring(0, parameterStart);
            if (name.equals(".") || name.equals(".."))
                return true;
            if (name.isEmpty() && i < segments.length - 1)
                return true;
        }
        return false;
    }

    /**
     * Returns the request target that is used to select an API: path parameters are removed and duplicate slashes
     * are merged, e.g. "//api;jsessionid=1//admin?q" becomes "/api/admin?q". Servlet containers and many web
     * servers do the same when they map a request, so a path that differs only in these details must not dodge a
     * more specific API. The forwarded request keeps the target unchanged.
     */
    public static String toRoutingPath(String uri) {
        return mergeSlashes(removePathParameters(uri));
    }

    /**
     * Merges duplicate slashes in the path of an origin-form request target, e.g. "//api///admin?x=//y" becomes
     * "/api/admin?x=//y". The query is left untouched. Other forms are returned unchanged.
     */
    static String mergeSlashes(String uri) {
        if (!uri.startsWith("/"))
            return uri;

        final var queryStart = uri.indexOf('?');
        final var path = queryStart == -1 ? uri : uri.substring(0, queryStart);
        if (!path.contains("//"))
            return uri;

        final var query = queryStart == -1 ? "" : uri.substring(queryStart);
        return MULTIPLE_SLASHES.matcher(path).replaceAll("/") + query;
    }

    /**
     * Removes the path parameters (";...", RFC 3986 3.3) from every segment of an origin-form request target,
     * e.g. "/api;jsessionid=1/admin?q" becomes "/api/admin?q". Servlet containers ignore them when mapping a
     * request, so routing has to ignore them too. The forwarded request keeps them.
     * The query is left untouched. Other forms are returned unchanged.
     */
    public static String removePathParameters(String uri) {
        if (!uri.startsWith("/"))
            return uri;

        final var queryStart = uri.indexOf('?');
        final var path = queryStart == -1 ? uri : uri.substring(0, queryStart);
        if (path.indexOf(';') == -1)
            return uri;

        final var query = queryStart == -1 ? "" : uri.substring(queryStart);
        return Arrays.stream(path.split("/", -1))
                       .map(URIUtil::removeParameters)
                       .collect(joining("/")) + query;
    }

    private static String removeParameters(String segment) {
        final var parameterStart = segment.indexOf(';');
        return parameterStart == -1 ? segment : segment.substring(0, parameterStart);
    }

    /**
     * A dot-segment always starts directly after a slash.
     */
    private static boolean containsDotSegment(String path) {
        return path.contains("/.");
    }

    /**
     * Decodes percent-encoded unreserved characters (ALPHA, DIGIT, "-", ".", "_", "~"), which are equivalent to
     * their plain form (RFC 3986 2.3). Everything else, including reserved characters like %2F and %3B,
     * stays encoded as it is, since decoding it would change the meaning of the path.
     *
     * @throws IllegalArgumentException if a "%" is not followed by two hex digits. Keeping it would let the
     *                                  decoded characters behind it form a new escape, e.g. "/%6%31dmin" would
     *                                  become "/%61dmin".
     */
    static String decodeUnreserved(String path) {
        if (path.indexOf('%') == -1)
            return path;

        final var sb = new StringBuilder(path.length());
        for (var i = 0; i < path.length(); i++) {
            final var c = path.charAt(i);
            if (c == '%') {
                if (i + 2 >= path.length() || !isHex(path.charAt(i + 1)) || !isHex(path.charAt(i + 2)))
                    throw new IllegalArgumentException("Invalid percent-encoding in path at index %d".formatted(i));

                final var decoded = (char) Integer.parseInt(path, i + 1, i + 3, 16);
                if (isUnreserved(decoded)) {
                    sb.append(decoded);
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
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

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    private static boolean isUnreserved(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
               || c == '-' || c == '.' || c == '_' || c == '~';
    }
}
