package com.predic8.membrane.core.util;

import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.*;

import static java.util.stream.Collectors.*;

/**
 * Utility methods for parsing attribute values that may be comma- or space-separated.
 */
public final class StringList {

    private StringList() { }   // static utility

    /**
     * Parse a string into a collection of trimmed, non-empty tokens.
     *
     * <p>Separators:
     * <ul>
     *   <li>Comma(+ optional surrounding blanks), e.g. {@code "GET, POST"}</li>
     *   <li>One or more blank characters, e.g. {@code "GET POST"}</li>
     * </ul></p>
     *
     * @param value            input string; {@code null} or blank ? empty collection
     * @param collectionFactory supplies the concrete collection implementation
     *                          (e.g. {@code ArrayList::new}, {@code LinkedHashSet::new})
     * @param <C>              concrete collection type
     * @return newly created collection (never {@code null})
     *
     * @since 6.2.0
     */
    public static <C extends Collection<String>> @NotNull C parse(String value, Supplier<C> collectionFactory) {

        String v = value == null ? "" : value;
        return Arrays.stream(v.split("\\s*,\\s*|\\s+"))   // comma OR whitespace
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(toCollection(collectionFactory));
    }

    /** Shortcut returning a mutable {@link ArrayList}. */
    public static @NotNull List<String> parseToList(String value) {
        return parse(value, ArrayList::new);
    }

    /** Shortcut returning a mutable {@link LinkedHashSet} (keeps order, no dups). */
    public static @NotNull Set<String> parseToSet(String value) {
        return parse(value, LinkedHashSet::new);
    }
}

