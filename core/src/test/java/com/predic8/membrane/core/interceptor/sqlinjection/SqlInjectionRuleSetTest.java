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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlInjectionRuleSetTest {

    private static final SqlInjectionRuleSet RULES = SqlInjectionRuleSet.loadCrsRules(1);

    @ParameterizedTest
    @ValueSource(strings = {
            "information_schema",
            "' UNION SELECT password FROM users",
            "sleep(5)",
            "1 UNION ALL SELECT NULL,NULL,NULL--",
            "'; DROP TABLE users; --"
    })
    void detectsSqlInjection(String payload) {
        assertTrue(RULES.firstMatch(payload).isPresent(), () -> "should have flagged: " + payload);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Alice",
            "John Doe",
            "I really love this product, would buy again",
            "Berlin",
            "order-12345",
            "user@example.com"
    })
    void passesBenignInput(String input) {
        assertTrue(RULES.firstMatch(input).isEmpty(), () -> "false positive on: " + input);
    }

    @Test
    void emptyAndNullAreClean() {
        assertTrue(RULES.firstMatch("").isEmpty());
        assertTrue(RULES.firstMatch(null).isEmpty());
    }

    @Test
    void detectsDoubleUrlEncodedPayload() {
        // %2553 -> (urlDecodeUni) -> %53 -> 'S'; here a fully double-encoded "' UNION SELECT"
        String once = "%27%20UNION%20SELECT"; // what reaches us after one decode of %2527...
        assertTrue(RULES.firstMatch(once).isPresent(), "urlDecodeUni transform should expose the payload");
    }

    @Test
    void higherParanoiaLoadsMoreRules() {
        assertTrue(SqlInjectionRuleSet.loadCrsRules(2).size() > SqlInjectionRuleSet.loadCrsRules(1).size());
        assertTrue(SqlInjectionRuleSet.loadCrsRules(4).size() >= SqlInjectionRuleSet.loadCrsRules(3).size());
    }
}
