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

import org.jetbrains.annotations.*;

import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

import static java.util.regex.Pattern.*;
import static java.util.stream.Collectors.*;

public class UriTemplateMatcher {

    public static final String PARAM_NAME_REGEX = "\\{([^/]+?)}";
    public static final String URI_PARAM_MATCH = "([^/]+)";
    private static final Pattern PARAM_NAME_REGEX_PATTERN = compile(PARAM_NAME_REGEX);

    /**
     * Problem:
     *  - How to handle parameter names containing _, -, ...
     *
     * @return Map of Parameters. If the path does not match PathDoesNotMatchException is thrown.
     */
    public static Map<String, String> matchTemplate(String template, String uri) throws PathDoesNotMatchException {
        if (template == null || uri == null)
            return null;

        Matcher parameters = getTemplateMatcher(template, uri);

        if (!parameters.matches()) {
            throw new PathDoesNotMatchException();
        }

        List<String> names = getParameterNames(template);

        return IntStream.range(0, names.size())
                .boxed()
                .collect(toMap(
                        names::get,
                        i -> URLDecoder.decode(parameters.group(i + 1), StandardCharsets.UTF_8)
                ));
    }

    static List<String> getParameterNames(String uriTemplate) {
        return getNameMatcher(normalizePath(uriTemplate)).results().map(r -> r.group(1)).toList();
    }

    @NotNull
    private static Matcher getTemplateMatcher(String template, String path) {
        return compile(prepareTemplate(normalizePath(template))).matcher(normalizePath(path));
    }

    @NotNull
    static Matcher getNameMatcher(String normalizedTemplate) {
        return PARAM_NAME_REGEX_PATTERN.matcher(normalizedTemplate);
    }

    static String prepareTemplate(String template) {
        return template.replaceAll(PARAM_NAME_REGEX, URI_PARAM_MATCH);
    }

    static String normalizePath(String path) {
        String normalizedPath = path.split("\\?")[0];
        return normalizedPath.endsWith("/") ? normalizedPath : normalizedPath + "/";
    }
}
