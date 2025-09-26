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

import org.jetbrains.annotations.*;

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

    public static @NotNull String join(List<String> l) {
        return String.join(", ", l);
    }

    public static @NotNull String join(Set<String> s) {
        return join(new ArrayList<>(s));
    }

    /**
     * @param it Iterator
     * @return Number of items that Iterator provides
     */
    public static int count(Iterator<?> it) {
        int cnt = 0;
        while (it.hasNext()) {
            it.next();
            cnt++;
        }
        return cnt;
    }

}