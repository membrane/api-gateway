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

package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

public class CorsUtil {

    public static final String SPACE = " ";

    public static String getNormalizedOrigin(Exchange exc) {
        String origin = exc.getRequest().getHeader().getFirstValue(ORIGIN);
        if (origin == null) return null;
        return normalizeOrigin(origin);
    }

    public static @NotNull String normalizeOrigin(String origin) {
        return origin.toLowerCase().replaceAll("/+$", "");
    }

    public static @NotNull Set<String> splitBySpace(String origins) {
        return stream(origins.split(SPACE))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(toSet());
    }
}
