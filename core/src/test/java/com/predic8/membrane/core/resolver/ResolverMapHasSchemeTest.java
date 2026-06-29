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
package com.predic8.membrane.core.resolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.predic8.membrane.core.resolver.ResolverMap.hasScheme;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests  {@code ResolverMap.hasScheme(String)}. It backs the
 * "child already has its own scheme, return it verbatim" branch of {@code combine}; the cases
 * below pin down the SCHEME pattern: a scheme must start the string and be followed by "/" or "\".
 */
public class ResolverMapHasSchemeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://predic8.de",
            "https://api.predic8.de/shop/",
            "file:/chi/elm",
            "file:///foo",
            "classpath:/openapi/spec.yml",
            "classpath:\\openapi\\spec.yml",
            "internal:/foo",
            "a:/x",                 // single-letter scheme
            "git+ssh://host/repo"   // scheme may contain '+', '.', '-'
    })
    void detectsScheme(String location) {
        assertTrue(hasScheme(location), () -> "Expected a scheme in: " + location);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "array.yml",
            "a/b/c",
            "/chi/elm",                     // absolute path, not a scheme
            "../conf/ArticleType.xsd",      // relative path
            "openapi/specs/foo"
    })
    void detectsNoScheme(String location) {
        assertFalse(hasScheme(location), () -> "Expected no scheme in: " + location);
    }

    @Test
    void emptyStringHasNoScheme() {
        assertFalse(hasScheme(""));
    }

    @Test
    void colonWithoutSlashIsNotAScheme() {
        // The pattern requires a "/" or "\" right after the colon, so "mailto:" is not detected.
        assertFalse(hasScheme("mailto:user@host"));
    }

    @Test
    void schemeInsideQueryIsNotMistakenForChildScheme() {
        // Documented edge case: the SCHEME pattern is anchored at the start, so a scheme appearing
        // later (e.g. inside a query parameter) is not treated as the child's own scheme.
        assertFalse(hasScheme("/foo?a=http://dummy"));
    }
}
