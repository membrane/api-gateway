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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import java.util.*;
import java.util.regex.*;

/**
 * Maps a request path and method onto the name of the WSDL operation that serves it. Knows nothing
 * about WSDL: {@link Wsdl2OpenapiInterceptor} builds the routes from the WSDL and the configuration
 * and hands them over.
 */
class OperationRouter {

    /** One exposed operation, reachable under {@code pathPattern} with {@code method}. */
    record RouteEntry(Pattern pathPattern, List<String> paramNames, String method, String operationName) {}

    record RouteMatch(String operationName, Map<String, String> pathParams) {}

    private final String basePath;
    private final List<RouteEntry> routes;

    OperationRouter(String basePath, List<RouteEntry> routes) {
        this.basePath = basePath;
        this.routes = List.copyOf(routes);
    }

    Optional<RouteMatch> match(String path, String method) {
        String segment = pathSegment(path);
        for (var entry : routes) {
            if (!entry.method().equalsIgnoreCase(method)) continue;
            Matcher m = entry.pathPattern().matcher(segment);
            if (m.matches()) {
                var params = new LinkedHashMap<String, String>();
                for (int i = 0; i < entry.paramNames().size(); i++) {
                    params.put(entry.paramNames().get(i), m.group(i + 1));
                }
                return Optional.of(new RouteMatch(entry.operationName(), params));
            }
        }
        return Optional.empty();
    }

    /** The methods registered for the route(s) matching {@code path}, in declaration order. */
    List<String> allowedMethods(String path) {
        String segment = pathSegment(path);
        return routes.stream()
                .filter(entry -> entry.pathPattern().matcher(segment).matches())
                .map(RouteEntry::method)
                .distinct()
                .toList();
    }

    List<RouteEntry> getRoutes() {
        return routes;
    }

    /** Strips the base path and any query string, leaving the segment the routes are matched against. */
    private String pathSegment(String path) {
        String withoutBase = path.replaceFirst("^" + Pattern.quote(basePath), "");
        if (withoutBase.startsWith("/")) withoutBase = withoutBase.substring(1);
        return withoutBase.contains("?") ? withoutBase.substring(0, withoutBase.indexOf('?')) : withoutBase;
    }

    /** The names of the {@code {placeholder}}s of a path template, in order of appearance. */
    static List<String> extractParamNames(String template) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(template);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    /** A path template with every {@code {placeholder}} turned into a capturing group. */
    static Pattern buildPathPattern(String template) {
        StringBuilder sb = new StringBuilder("^");
        Matcher m = Pattern.compile("\\{[^}]+}").matcher(template);
        int last = 0;
        while (m.find()) {
            sb.append(Pattern.quote(template.substring(last, m.start())));
            sb.append("([^/]+)");
            last = m.end();
        }
        sb.append(Pattern.quote(template.substring(last)));
        sb.append("$");
        return Pattern.compile(sb.toString());
    }
}
