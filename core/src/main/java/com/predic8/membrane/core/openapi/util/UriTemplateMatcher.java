package com.predic8.membrane.core.openapi.util;

import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.PathUtils.trimQueryString;

public class UriTemplateMatcher {

    private  static final Pattern pathParameterNamePattern = Pattern.compile("\\{(.*?)\\}");

    /**
     *
     * @param template
     * @param uri
     * @return Map of Parameters. If the path does not match null is returned.
     */
    public Map<String,String> match(String template, String uri) throws PathDoesNotMatchException {
        final Matcher matcher = Pattern.compile(escapeSlash(prepareRegex(template))).matcher(trimQueryString(uri));

        final List<String> parameterNames = getPathParameterNames(template);

        Map<String,String> pathParameters = new HashMap();

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
        return uriTemplate.replaceAll("\\{(.*?)\\}","(.*)");
    }

    public String escapeSlash(String s) {
        return s.replaceAll("\\/","\\\\/");
    }


    public List<String> getPathParameterNames(String uriTemplate) {
        final Matcher matcher = pathParameterNamePattern.matcher(uriTemplate);

        List variables = new ArrayList();
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                variables.add(matcher.group(i));
            }
        }
        return variables;
    }
}
