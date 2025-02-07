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

import java.io.*;
import java.net.URI;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Optional.*;

public class URIUtil {

    private static final Pattern driveLetterPattern = Pattern.compile("^(\\w)[/:|].*");

    public static String toFileURIString(File f) throws URISyntaxException {
        return convertPath2FileURI(f.getAbsolutePath()).toString();
    }

    /**
     *
     * @param path Filepath like /foo/boo
     * @return
     * @throws URISyntaxException
     */
    public static java.net.URI convertPath2FileURI(String path) throws URISyntaxException {
        return new URI( addFilePrefix(encodePathCharactersForUri(path)));
    }

    public static String encodePathCharactersForUri(String s) {
        return s.replaceAll(" ","%20").replace("\\","/");
    }

    private static @NotNull String addFilePrefix(String path) {
        if (!path.startsWith("file:"))
            path = "file:" + path;
        if (path.charAt(5) != '/')
            path = "file:/" + path.substring(5);
        return path;
    }

    public static String convertPath2FilePathString(String path) {
        path = addFilePrefix(path);
        return path.replaceAll(" ","%20");
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
        Matcher m = driveLetterPattern.matcher(p);
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

}