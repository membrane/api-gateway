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

import java.util.*;
import java.util.regex.*;

import static java.util.regex.Pattern.*;

public class UriTemplateMatcher {

    /**
     * OpenAPI template-expression-param-name: every character but { and }, including /, ? and #.
     */
    public static final String PARAM_NAME_REGEX = "\\{([^{}]+)}";
    /**
     * OpenAPI: the value of a path parameter MUST NOT contain an unescaped /, ? or #.
     */
    public static final String URI_PARAM_MATCH = "[^/?#]+";
    private static final Pattern PARAM_NAME_REGEX_PATTERN = compile(PARAM_NAME_REGEX);

    /**
     * Problem:
     *  - How to handle parameter names containing _, -, ...
     *
     * Compiles the template on every call. On hot paths keep a {@link UriTemplate} instead.
     * The literal parts of the template are regular expressions, e.g. <code>.*&#47;api/{name}</code>.
     *
     * @return Map of Parameters. If the path does not match PathDoesNotMatchException is thrown.
     */
    public static Map<String, String> matchTemplate(String template, String uri) throws PathDoesNotMatchException {
        if (template == null || uri == null)
            return null;

        return UriTemplate.of(template, true).match(uri).orElseThrow(PathDoesNotMatchException::new);
    }

    static List<String> getParameterNames(String uriTemplate) {
        return getNameMatcher(uriTemplate).results().map(r -> r.group(1)).toList();
    }

    @NotNull
    static Matcher getNameMatcher(String normalizedTemplate) {
        return PARAM_NAME_REGEX_PATTERN.matcher(normalizedTemplate);
    }

    static String prepareTemplate(String template) {
        return prepareTemplate(template, true);
    }

    /**
     * Each parameter becomes a named group, so that capturing groups in the literal parts do not shift the lookup.
     *
     * @param regExp true if the literal parts are regular expressions, otherwise they are matched as they are
     */
    static String prepareTemplate(String template, boolean regExp) {
        final var regex = new StringBuilder();
        final var matcher = getNameMatcher(template);
        var index = 0;
        var literalStart = 0;
        while (matcher.find()) {
            regex.append(literal(template.substring(literalStart, matcher.start()), regExp))
                    .append("(?<%s>%s)".formatted(groupName(index++), URI_PARAM_MATCH));
            literalStart = matcher.end();
        }
        return regex.append(literal(template.substring(literalStart), regExp)).toString();
    }

    private static String literal(String literal, boolean regExp) {
        if (regExp || literal.isEmpty())
            return literal;
        if (literal.contains("{") || literal.contains("}"))
            throw new IllegalArgumentException("Curly brace in %s is not part of a template expression.".formatted(literal));
        return Pattern.quote(literal);
    }

    static String groupName(int parameterIndex) {
        return "param" + parameterIndex;
    }

    static String normalizePath(String path) {
        return appendSlash(path.split("\\?")[0]);
    }

    /**
     * Unlike {@link #normalizePath(String)} nothing is cut off at a <code>?</code>, which may be part of a parameter name.
     */
    static String normalizeTemplate(String template) {
        return appendSlash(template);
    }

    private static String appendSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }
}
