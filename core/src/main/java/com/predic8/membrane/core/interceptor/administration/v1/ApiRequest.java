/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.exchange.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;

/**
 * A matched admin API request: the exchange, the decoded path parameters of the route and the query parameters.
 */
record ApiRequest(Exchange exchange, List<String> pathParams, Map<String, List<String>> query) {

    String path(int index) {
        return pathParams.get(index);
    }

    long pathLong(int index) {
        try {
            return Long.parseLong(path(index));
        } catch (NumberFormatException e) {
            throw notFound("Unknown id.");
        }
    }

    Optional<String> param(String name) {
        final var values = query.get(name);
        if (values == null || values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank())
            return Optional.empty();
        return Optional.of(values.getFirst());
    }

    /**
     * Values of a list parameter, accepting both {@code a=1,2} and {@code a=1&a=2}.
     */
    List<String> params(String name) {
        final var values = query.getOrDefault(name, List.of());
        final var result = new ArrayList<String>();
        for (final var value : values) {
            if (value == null)
                continue;
            for (final var part : value.split(","))
                if (!part.isBlank())
                    result.add(part.trim());
        }
        return List.copyOf(result);
    }

    /**
     * Values of a repeatable parameter whose values may contain commas.
     */
    List<String> repeated(String name) {
        return query.getOrDefault(name, List.of()).stream()
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .toList();
    }

    int intParam(String name, int defaultValue, int min, int max) {
        final var value = param(name);
        if (value.isEmpty())
            return defaultValue;
        final int i;
        try {
            i = Integer.parseInt(value.get());
        } catch (NumberFormatException e) {
            throw badRequest("Query parameter '%s' must be an integer.".formatted(name));
        }
        if (i < min || i > max)
            throw badRequest("Query parameter '%s' must be between %d and %d.".formatted(name, min, max));
        return i;
    }

    String enumParam(String name, String defaultValue, Set<String> allowed) {
        final var value = param(name).orElse(defaultValue);
        if (!allowed.contains(value))
            throw badRequest("Query parameter '%s' must be one of %s.".formatted(name, new TreeSet<>(allowed)));
        return value;
    }
}
