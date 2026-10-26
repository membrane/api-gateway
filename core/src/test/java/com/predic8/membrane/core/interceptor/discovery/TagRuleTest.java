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
package com.predic8.membrane.core.interceptor.discovery;

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.discovery.TagRule.*;
import static org.junit.jupiter.api.Assertions.*;

class TagRuleTest {

    private static TagRule include(String tag) {
        final var rule = new Include();
        rule.setTag(tag);
        return rule;
    }

    private static TagRule exclude(String tag) {
        final var rule = new Exclude();
        rule.setTag(tag);
        return rule;
    }

    @Test
    void withoutRulesEveryApiIsListed() {
        assertTrue(isListed(List.of(), List.of()));
    }

    @Test
    void aPlainTagMatchesExactly() {
        assertTrue(isListed(List.of(include("internal")), List.of("internal")));
        assertFalse(isListed(List.of(include("internal")), List.of("internal-tools")));
    }

    @Test
    void aRegexMatchesTheWholeTag() {
        assertTrue(isListed(List.of(include("int.*")), List.of("internal")));
        assertFalse(isListed(List.of(include("ext")), List.of("external")));
    }

    @Test
    void theFirstMatchingRuleDecides() {
        final var rules = List.of(exclude("partner-.*"), include(".*"));

        assertFalse(isListed(rules, List.of("partner-acme")));
        assertTrue(isListed(rules, List.of("external")));
    }

    @Test
    void aValueThatIsNoValidRegexIsARejectedConfiguration() {
        final var e = assertThrows(ConfigurationException.class, () -> include("[beta"));
        assertTrue(e.getMessage().contains("[beta"));
    }

    @Test
    void anApiNoRuleMatchesIsLeftOut() {
        assertFalse(isListed(List.of(exclude("internal")), List.of("external")));
        assertFalse(isListed(List.of(exclude("internal")), List.of()));
    }
}
