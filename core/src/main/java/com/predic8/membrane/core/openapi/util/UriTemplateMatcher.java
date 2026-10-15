/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

public class UriTemplateMatcher {

    public static final String PARAM_NAME_REGEX = "\\{([^/]+?)}";
    public static final String URI_PARAM_MATCH = "[^/]+";
    private static final Pattern PARAM_NAME_REGEX_PATTERN = compile(PARAM_NAME_REGEX);

    /**
     * Problem:
     *  - How to handle parameter names containing _, -, ...
     *
     * Compiles the template on every call. On hot paths keep a {@link UriTemplate} instead.
     *
     * @return Map of Parameters. If the path does not match PathDoesNotMatchException is thrown.
     */
    public static Map<String, String> matchTemplate(String template, String uri) throws PathDoesNotMatchException {
        if (template == null || uri == null)
            return null;

        return UriTemplate.of(template).match(uri).orElseThrow(PathDoesNotMatchException::new);
    }

    static List<String> getParameterNames(String uriTemplate) {
        return getNameMatcher(normalizePath(uriTemplate)).results().map(r -> r.group(1)).toList();
    }

    @NotNull
    static Matcher getNameMatcher(String normalizedTemplate) {
        return PARAM_NAME_REGEX_PATTERN.matcher(normalizedTemplate);
    }

    /**
     * Each parameter becomes a named group, so that capturing groups in the literal parts do not shift the lookup.
     */
    static String prepareTemplate(String template) {
        final var index = new AtomicInteger();
        return PARAM_NAME_REGEX_PATTERN.matcher(template)
                .replaceAll(r -> "(?<%s>%s)".formatted(groupName(index.getAndIncrement()), URI_PARAM_MATCH));
    }

    static String groupName(int parameterIndex) {
        return "param" + parameterIndex;
    }

    static String normalizePath(String path) {
        String normalizedPath = path.split("\\?")[0];
        return normalizedPath.endsWith("/") ? normalizedPath : normalizedPath + "/";
    }
}
