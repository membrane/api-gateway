/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import static java.util.regex.Pattern.compile;

public class UriTemplateMatcher {

    public static final String TEMPLATE_PARAM_MATCH = "\\{()}";
    public static final String TEMPLATE_PARAM_MATCH = "\\{([^/]+)}";
    public static final String URI_PARAM_MATCH = "(?<$1>[^/]+)";

    /**
     * @return Map of Parameters. If the path does not match null is returned.
     */
    public Map<String, String> match(String template, String uri) throws PathDoesNotMatchException {
        Map<String, String> params = new HashMap<>();
        String normalizedTemplate = normalizePath(template);
        Matcher matcher = compile(prepareTemplate(normalizedTemplate)).matcher(normalizePath(uri));

        if (!matcher.find()) {
            throw new PathDoesNotMatchException();
        }

        for (int i = 1; i <= matcher.groupCount(); i++) {
            System.out.println("matcher = " + matcher.group(i));
            String key = ""+i;
            String value = matcher.group(i);
            params.put(key, value);
        }

        return params;
    }

    static String prepareTemplate(String template) {
        return template.replaceAll(TEMPLATE_PARAM_MATCH, URI_PARAM_MATCH);
    }

    static String normalizePath(String path) {
        String normalizedPath = path.split("\\?")[0];
        return normalizedPath.endsWith("/") ? normalizedPath : normalizedPath + "/";
    }
}
