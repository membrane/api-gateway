package com.predic8.membrane.core.util;

import java.util.*;
import java.util.regex.*;

import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Optional.empty;

public class URIUtil {

    private static final Pattern driveLetterPattern = Pattern.compile("^(\\w)[/:|].*");

    /**
     * Removes file protocol from uri
     * @param uri path that can contain file protocol
     * @return path without the file protocol
     */
    public static String pathFromFileURI(String uri) {
        if (!uri.startsWith("file:"))
            return uri;
        return decode(processDecodedPart(stripFilePrefix(uri)),UTF_8);
    }

    private static String processDecodedPart(String path) {
        if (path.charAt(0) != '/')
            return path;
        Optional<String> drive = getPossibleDriveLetter(removeLocalhost(removeLeadingSlashes(path)));
        return drive.map(s -> "%s:\\%s".formatted(s, slashToBackslash(removeDriveLetterAndSlash(path)))).orElseGet(() -> "/" + path);

    }

    static String removeDriveLetterAndSlash(String path) {
        return path.replaceFirst("\\w[:?|/]/*", "");
    }

    static String slashToBackslash(String path) {
        return path.replace('/','\\');
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
        return uri.substring(5); // Remove "file:"
    }
}