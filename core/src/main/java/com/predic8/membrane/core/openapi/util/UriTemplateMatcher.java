/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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

import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.UriUtil.trimQueryString;

public class UriTemplateMatcher {

    private  static final Pattern pathParameterNamePattern = Pattern.compile("\\{(.*?)}");

    /**
     *
     * @return Map of Parameters. If the path does not match null is returned.
     */
    public Map<String,String> match(String template, String uri) throws PathDoesNotMatchException {
        final Matcher matcher = Pattern.compile(escapeSlash(prepareRegex(template))).matcher(trimQueryString(uri));

        final List<String> parameterNames = getPathParameterNames(template);

        Map<String,String> pathParameters = new HashMap<>();

        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                pathParameters.put(parameterNames.get(i-1), matcher.group(i));
            }
        }

        if (!matcher.matches())
            throw new PathDoesNotMatchException();

        return pathParameters;
    }

    public String prepareRegex(String uriTemplate) {
        return uriTemplate.replaceAll("\\{(.*?)}","(.*)");
    }

    public String escapeSlash(String s) {
        return s.replaceAll("/","\\\\/");
    }


    public List<String> getPathParameterNames(String uriTemplate) {
        final Matcher matcher = pathParameterNamePattern.matcher(uriTemplate);

        List<String> variables = new ArrayList<>();
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                variables.add(matcher.group(i));
            }
        }
        return variables;
    }
}
