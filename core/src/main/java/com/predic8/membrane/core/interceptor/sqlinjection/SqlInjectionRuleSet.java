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

package com.predic8.membrane.core.interceptor.sqlinjection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An immutable, ordered set of {@link SqlInjectionRule}s.
 * <p>
 * The bundled default set is transpiled from the OWASP CRS REQUEST-942 SQL
 * injection rules (see {@code distribution/crs-sqli-rules/}). Rules above the configured
 * paranoia level are filtered out at load time so matching only pays for the
 * rules that are active.
 * <p>
 * Derived from the OWASP Core Rule Set, Apache License 2.0, Copyright (c) Trustwave and
 * contributors and the CRS project. Source: <a href="https://github.com/coreruleset/coreruleset">
 * https://github.com/coreruleset/coreruleset</a>.
 */
public class SqlInjectionRuleSet {

    private static final String CRS_RESOURCE = "crs-sqli-rules.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<SqlInjectionRule> rules;

    public SqlInjectionRuleSet(List<SqlInjectionRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * Load the bundled CRS rules, keeping only those at or below {@code maxParanoiaLevel}.
     */
    public static SqlInjectionRuleSet loadCrsRules(int maxParanoiaLevel) {
        try (InputStream is = SqlInjectionRuleSet.class.getResourceAsStream(CRS_RESOURCE)) {
            if (is == null)
                throw new IllegalStateException("Bundled rule resource not found: " + CRS_RESOURCE);
            return new SqlInjectionRuleSet(parse(MAPPER.readTree(is), maxParanoiaLevel));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read bundled SQL injection rules", e);
        }
    }

    private static List<SqlInjectionRule> parse(JsonNode root, int maxParanoiaLevel) {
        List<SqlInjectionRule> result = new ArrayList<>();
        for (JsonNode n : root) {
            int pl = n.path("paranoiaLevel").asInt(1);
            if (pl > maxParanoiaLevel)
                continue;
            result.add(new SqlInjectionRule(
                    n.path("id").asText(),
                    pl,
                    List.copyOf(getTransformations(n)),
                    n.path("message").asText(),
                    Pattern.compile(n.path("regex").asText()),
                    getRequires(n)));
        }
        return result;
    }

    private static @NotNull List<Transformation> getTransformations(JsonNode n) {
        List<Transformation> transforms = new ArrayList<>();
        for (var t : n.path("transforms"))
            transforms.add(Transformation.valueOf(t.asText()));
        return transforms;
    }

    private static @Nullable Pattern getRequires(JsonNode n) {
        return n.hasNonNull("requires") ? Pattern.compile(n.get("requires").asText()) : null;
    }

    /**
     * @return the first rule violated by {@code input}, or empty if it looks clean.
     */
    public Optional<SqlInjectionRule> firstMatch(String input) {
        if (input == null || input.isEmpty())
            return Optional.empty();
        for (SqlInjectionRule rule : rules)
            if (rule.matches(input))
                return Optional.of(rule);
        return Optional.empty();
    }

    public int size() {
        return rules.size();
    }

    /**
     * @return the loaded rules in match order (immutable). Useful for testing an individual rule's
     * pattern in isolation, e.g. when a stricter rule is shadowed by a lower-numbered one during {@link #firstMatch}.
     */
    public List<SqlInjectionRule> rules() {
        return rules;
    }
}
