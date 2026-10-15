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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * The literal parts of the template are used as regular expression, e.g. <code>.*&#47;api/{name}</code>.
 * Immutable and thread safe.
 */
public final class UriTemplate {

    private final String template;
    private final Pattern pattern;
    private final List<String> parameterNames;

    private UriTemplate(String template) {
        this.template = template;
        this.pattern = compile(prepareTemplate(normalizePath(template)));
        this.parameterNames = getParameterNames(template);
    }

    /**
     * @throws PatternSyntaxException if the template is no valid regular expression after replacing the parameters
     */
    public static UriTemplate of(String template) {
        return new UriTemplate(template);
    }

    /**
     * @param uri path, may contain a query string, which is ignored
     * @return URL-decoded parameter values by name, or empty if the path does not match the template
     */
    public Optional<Map<String, String>> match(String uri) {
        final var matcher = pattern.matcher(normalizePath(uri));
        if (!matcher.matches())
            return Optional.empty();

        return Optional.of(IntStream.range(0, parameterNames.size())
                .boxed()
                .collect(toMap(
                        parameterNames::get,
                        i -> URLDecoder.decode(matcher.group(groupName(i)), StandardCharsets.UTF_8)
                )));
    }

    public boolean matches(String uri) {
        return pattern.matcher(normalizePath(uri)).matches();
    }

    public String getTemplate() {
        return template;
    }

    @Override
    public String toString() {
        return template;
    }
}
