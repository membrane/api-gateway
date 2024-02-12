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

import static java.util.regex.Pattern.compile;

public class UriTemplateMatcher {

    public static final String PARAM_NAME_REGEX = "\\{([\\w_]+?)}";
    public static final String URI_PARAM_MATCH = "(?<$1>[^/]+)";
    private static final Pattern PARAM_NAME_REGEX_PATTERN = compile(PARAM_NAME_REGEX);

    /**
     * Problem:
     *  - How to handle parameter names containing _, -, ...
     *
     * @return Map of Parameters. If the path does not match PathDoesNotMatchException is thrown.
     */
    public Map<String, String> match(String template, String uri) throws PathDoesNotMatchException {

        String normalizedTemplate = normalizePath(template);

        // Is needed to get the parameter names
        Matcher nameMatcher = getNameMatcher(normalizedTemplate);
        String preparedTemplate = prepareTemplate(normalizedTemplate);

        // Is needed to get the parameters by name
        Matcher paramMatcher = compile(preparedTemplate).matcher(normalizePath(uri));

        // Checks with URI matches template
        if (!paramMatcher.matches()) {
            throw new PathDoesNotMatchException();
        }

        Map<String, String> params = new HashMap<>();
        while (nameMatcher.find()) {
            String paramName = nameMatcher.group(1); // Get next name like 'id'
            params.put(paramName, paramMatcher.group(paramName));
        }

        return params;
    }

    static List<String> getParameterNames(String uriTemplate) {
        return null;
    }

    @NotNull
    public static Matcher getNameMatcher(String normalizedTemplate) {
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
