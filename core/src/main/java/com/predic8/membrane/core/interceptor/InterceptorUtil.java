/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import java.util.*;
import java.util.function.*;

public class InterceptorUtil {

    public static <T extends Interceptor> List<T> getInterceptors(List<Interceptor> interceptors, Class<T> clazz) {
        return interceptors.stream().filter(i -> i.getClass().equals(clazz))
                .map(clazz::cast)
                .toList();
    }

    public static <T extends Interceptor> Optional<T> getFirstInterceptorOfType(List<Interceptor> interceptors, Class<T> type) {
        return getInterceptors(interceptors, type).stream().findFirst();
    }

    /**
     * Ensures that an interceptor of the given {@code type} is in the first position.
     * <p>
     * Behavior:
     * - If an interceptor of exactly {@code type} exists, the first occurrence is moved to index 0.
     * - If none exists, a new instance is created using {@code supplier}, inserted at index 0, and returned.
     * - Returns {@link Optional#empty()} only if {@code supplier} returns {@code null}.
     * <p>
     * Notes:
     * - Matches by exact class equality (same as {@link #getInterceptors(List, Class)}).
     * - Modifies the provided list in place.
     */
    public static <T extends Interceptor> Optional<T> moveToFirstPosition(
            List<Interceptor> interceptors,
            Class<T> type,
            Supplier<T> supplier
    ) {
        // Find first exact match
        for (int i = 0; i < interceptors.size(); i++) {
            Interceptor current = interceptors.get(i);
            if (current != null && current.getClass().equals(type)) {
                @SuppressWarnings("unchecked")
                T typed = (T) current;

                if (i != 0) {
                    // Remove and re-add at front
                    interceptors.remove(i);
                    interceptors.addFirst(typed);
                }
                return Optional.of(typed);
            }
        }

        // Not found: create and add
        T created = supplier.get();
        if (created == null) {
            return Optional.empty();
        }
        interceptors.addFirst(created);
        return Optional.of(created);
    }

}

