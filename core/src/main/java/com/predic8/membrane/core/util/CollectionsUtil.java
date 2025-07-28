/* Copyright 2023 predic8 GmbH, www.predic8.com

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

import java.util.*;
import java.util.stream.*;

import static java.util.stream.Collectors.*;

public class CollectionsUtil {

    public static <T> List<T> concat(List<T> l1, List<T> l2) {
        return Stream.of(l1,l2).filter(Objects::nonNull).flatMap(Collection::stream).toList();
    }

    public static <T> List<T> toList(Iterator<T> iterator) {
        List<T> list = new ArrayList<>();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

//    /**
//     * Parses a string into a list of trimmed, non-empty string tokens.
//     *
//     * <p>This method supports two parsing modes:</p>
//     * <ul>
//     *   <li><strong>Comma-separated:</strong> Standard HTTP header format (e.g., "GET, POST, PUT")</li>
//     *   <li><strong>Space-separated:</strong> Alternative format (e.g., "GET POST PUT")</li>
//     * </ul>
//     *
//     * <p>The method automatically trims whitespace from each token and excludes empty values,
//     * making it robust against various formatting inconsistencies.</p>
//     *
//     * @param value the string to parse. Can be null or empty.
//     * @return a non-null list of parsed tokens. Returns an empty list if input is null/empty.
//
//     * @apiNote This dual parsing behavior is intended for flexibility in configuration formats.
//     * For strict HTTP header compliance, consider using comma-only separation.
//     * @since 6.2.0
//     */
//    public static @NotNull Set<String> parseCommaOrSpaceSeparated(String value) {
//        return stream(value.split("\\s*,\\s*|\\s+"))
//                .map(String::trim)
//                .filter(s -> !s.isEmpty())
//                .collect(toSet());
//    }

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
}