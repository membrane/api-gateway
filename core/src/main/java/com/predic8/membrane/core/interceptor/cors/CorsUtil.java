package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

public class CorsUtil {

    public static final String SPACE = " ";

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
                .collect(toSet());
    }

    public static String getNormalizedOrigin(Exchange exc) {
        String origin = exc.getRequest().getHeader().getFirstValue(ORIGIN);
        if (origin == null) return null;
        return normalizeOrigin(origin);
    }

    public static @NotNull String normalizeOrigin(String origin) {
        return origin.toLowerCase().replaceAll("/+$", "");
    }

    public static @NotNull String join(List<String> l) {
        return String.join(", ", l);
    }

    public static @NotNull Set<String> splitBySpace(String origins) {
        return stream(origins.split(SPACE))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(toSet());
    }
}
