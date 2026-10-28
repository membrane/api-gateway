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
package com.predic8.membrane.core.openapi.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.*;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toMap;

/**
 * A URI template like <code>/users/{id}</code>, compiled once so that it can be matched against many paths.
 * Matching a path that does not fit is cheap: no exception, no regex compilation.
 * <p>
 * By default the literal parts of the template are matched as they are, as in OpenAPI path templating. Optionally they
 * are regular expressions, e.g. <code>.*&#47;api/{name}</code>.
 * Immutable and thread safe.
 */
public final class UriTemplate {

    private final String template;
    private final boolean regExp;
    private final Pattern pattern;
    private final List<String> parameterNames;

    private UriTemplate(String template, boolean regExp) {
        this.template = template;
        this.regExp = regExp;
        this.pattern = compile(regExp ? prepareTemplate(template, true) : prepareTemplate(normalizeTemplate(template), false));
        this.parameterNames = getUniqueParameterNames(template);
    }

    /**
     * Literal parts are matched as they are, see OpenAPI path templating.
     *
     * @throws IllegalArgumentException if a parameter occurs more than once or a curly brace is not part of a template expression
     */
    public static UriTemplate of(String template) {
        return of(template, false);
    }

    /**
     * @param regExp true if the literal parts are regular expressions
     * @throws IllegalArgumentException if a parameter occurs more than once or, without regExp, a curly brace is not part of a template expression
     * @throws PatternSyntaxException   if regExp is true and the template is no valid regular expression after replacing the parameters
     */
    public static UriTemplate of(String template, boolean regExp) {
        return new UriTemplate(template, regExp);
    }

    private static List<String> getUniqueParameterNames(String template) {
        final var names = getParameterNames(template);
        final var seen = new HashSet<String>();
        for (final var name : names) {
            if (!seen.add(name))
                throw new IllegalArgumentException("Parameter {%s} occurs more than once in URI template %s.".formatted(name, template));
        }
        return names;
    }

    /**
     * @param uri path, may contain a query string, which is ignored
     * @return URL-decoded parameter values by name, or empty if the path does not match the template
     */
    public Optional<Map<String, String>> match(String uri) {
        final var matcher = matcher(uri);
        if (matcher == null)
            return Optional.empty();

        return Optional.of(IntStream.range(0, parameterNames.size())
                .boxed()
                .collect(toMap(
                        parameterNames::get,
                        i -> decodePathSegment(matcher.group(groupName(i)))
                )));
    }

    /**
     * Unlike in a form, a <code>+</code> in a path is no space.
     */
    private static String decodePathSegment(String segment) {
        return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    public boolean matches(String uri) {
        return matcher(uri) != null;
    }

    /**
     * A regular expression is matched against the path without the query as it is: unlike a template without regular
     * expressions, <code>^/foo$</code> does not match <code>/foo/</code>. Appending a slash to both would break
     * anchors, and wrapping the expression would break one like <code>\Q/foo</code> that quotes to the end.
     *
     * @return a matcher that matched the path, or null if the path does not match
     */
    private Matcher matcher(String uri) {
        final var matcher = pattern.matcher(regExp ? cutOffQuery(uri) : normalizePath(uri));
        return matcher.matches() ? matcher : null;
    }

    public String getTemplate() {
        return template;
    }

    @Override
    public String toString() {
        return template;
    }
}
