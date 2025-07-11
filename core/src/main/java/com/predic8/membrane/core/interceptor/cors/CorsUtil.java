package com.predic8.membrane.core.interceptor.cors;

import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.*;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

public class CorsUtil {

    /**
     * Convert to lower case and remove trailing slashes.
     * Maybe there is an other Util somewhere doing exactly this -> consolidate
     *
     * @param origin
     * @return normalized Origin
     */
    public static String normalizeOrigin(String origin) {
        if (origin == null) return null;
        return origin.toLowerCase().replaceAll("/+$", ""); //
    }

    /**
     * Parses a header value string into a list of trimmed, non-empty, lowercase string tokens.
     *
     * <p>This method supports two parsing modes:</p>
     * <ul>
     *   <li><strong>Comma-separated:</strong> Standard HTTP header format (e.g., "GET, POST, PUT")</li>
     *   <li><strong>Space-separated:</strong> Alternative format (e.g., "GET POST PUT")</li>
     * </ul>
     *
     * <p>The method automatically trims whitespace from each token and excludes empty values,
     * making it robust against various formatting inconsistencies.</p>
     *
     * @param value the header value string to parse. Can be null or empty.
     * @return a non-null list of parsed tokens. Returns an empty list if input is null/empty.

     * @apiNote This dual parsing behavior is intended for flexibility in configuration formats.
     * For strict HTTP header compliance, consider using comma-only separation.
     * @since 1.0
     */
    public static @NotNull Set<String> parseCommaOrSpaceSeparated(String value) {
        return stream(value.split("\\s*,\\s*|\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(toSet());
    }

    /**
     * Converts all strings in the given set to lowercase.
     *
     * @param strings the set of strings to convert
     * @return a new set containing all strings in lowercase
     */
    public static Set<String> toLowerCaseSet(Set<String> strings) {
        return strings.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

}
