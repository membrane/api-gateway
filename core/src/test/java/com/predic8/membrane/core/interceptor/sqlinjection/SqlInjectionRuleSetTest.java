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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SqlInjectionRuleSetTest {

    private static final SqlInjectionRuleSet RULES = SqlInjectionRuleSet.loadCrsRules(1);

    /** Cache one rule set per paranoia level so the parameterized tests don't reparse the JSON each row. */
    private static final SqlInjectionRuleSet[] BY_PARANOIA_LEVEL = {
            null,
            SqlInjectionRuleSet.loadCrsRules(1),
            SqlInjectionRuleSet.loadCrsRules(2),
            SqlInjectionRuleSet.loadCrsRules(3),
            SqlInjectionRuleSet.loadCrsRules(4),
    };

    /**
     * One crafted payload per bundled CRS rule. Each payload is designed so the named rule is the
     * <em>first</em> rule that fires when the rule set is loaded at the given paranoia level, which
     * pins every pattern down to a concrete, verifiable example.
     * <p>
     * Rules 942321 and 942530 are intentionally absent: their regexes are, respectively, identical to
     * 942320 and a strict subset of 942180, so a lower-numbered rule always matches first. They are
     * covered by {@link #shadowedRulesMatchWhenTestedInIsolation(String, int, String)} instead.
     */
    static Stream<Arguments> crsRules() {
        return Stream.of(
                arguments("a != b", 2, "942120"),
                arguments("information_schema", 1, "942140"),
                arguments("count(*)", 2, "942150"),
                arguments("benchmark(1)", 1, "942151"),
                arguments("elt(1,2)", 2, "942152"),
                arguments("sleep(5)", 1, "942160"),
                arguments("select if(1)", 1, "942170"),
                arguments("admin'", 2, "942180"),
                arguments("union select @@version", 1, "942190"),
                arguments("; drop table users", 2, "942210"),
                arguments("2147483648", 1, "942220"),
                arguments("(case when 1=1 then 1)", 1, "942230"),
                arguments("alter foo character set gbk", 1, "942240"),
                arguments("execute immediate 'x'", 1, "942250"),
                arguments(") having 1", 3, "942251"),
                arguments("name like '%admin'", 2, "942260"),
                arguments("union;select a from b", 1, "942270"),
                arguments("; shutdown --", 1, "942280"),
                arguments("$where", 1, "942290"),
                arguments("binary(1)", 2, "942300"),
                arguments("(select foo(", 2, "942310"),
                arguments("exec(@cmd)", 1, "942320"),
                arguments("foo table_name=1", 2, "942330"),
                arguments("1 except select 2", 2, "942340"),
                arguments("create function x returns int", 1, "942350"),
                arguments("foo end);", 1, "942360"),
                arguments("-1 union", 2, "942361"),
                arguments("1) as a from t", 2, "942362"),
                arguments("'a * 1", 2, "942370"),
                arguments("order by 1", 2, "942380"),
                arguments("1 or 5", 2, "942390"),
                arguments("1 and 5>3", 2, "942400"),
                arguments("rawtonhex(0)", 2, "942410"),
                arguments("!a!a!a!a!a!a!a!a", 3, "942420"),
                arguments("!a!a!a", 4, "942421"),
                arguments("!a!a!a!a!a!a!a!a!a!a!a!a", 2, "942430"),
                arguments("!a!a!a!a!a!a", 3, "942431"),
                arguments("!a!a", 4, "942432"),
                arguments("0xEF1234", 2, "942450"),
                arguments("....", 3, "942460"),
                arguments("sp_executesql", 2, "942470"),
                arguments("delete from users", 2, "942480"),
                arguments("'-1'", 3, "942490"),
                arguments("/*!12345 */", 1, "942500"),
                arguments("`abcdef`", 2, "942510"),
                arguments("'abcdef'", 3, "942511"),
                arguments("' is not", 2, "942520"),
                arguments("\\' or", 2, "942522"),
                arguments("abc';", 1, "942540"),
                arguments("json_extract(data,'$.x')", 1, "942550"),
                arguments("1.e(0)", 1, "942560")
        );
    }

    @ParameterizedTest(name = "[{index}] rule {2} flags \"{0}\"")
    @MethodSource("crsRules")
    void everyCrsRuleMatchesItsPayload(String payload, int paranoiaLevel, String expectedRuleId) {
        var match = BY_PARANOIA_LEVEL[paranoiaLevel].firstMatch(payload);
        assertTrue(match.isPresent(), () -> "should have flagged: " + payload);
        assertEquals(expectedRuleId, match.get().id(),
                () -> "expected rule " + expectedRuleId + " to be the first match for: " + payload);
    }

    /**
     * 942321 duplicates 942320 and 942530 is a subset of 942180, so a lower-numbered rule always wins
     * in {@link SqlInjectionRuleSet#firstMatch}. Their patterns are therefore exercised directly against
     * the individual {@link SqlInjectionRule} so the shadowed regex still gets real coverage.
     */
    @ParameterizedTest
    @MethodSource("shadowedRulePayloads")
    void shadowedRulesMatchWhenTestedInIsolation(String payload, int paranoiaLevel, String ruleId) {
        var rule = BY_PARANOIA_LEVEL[paranoiaLevel].rules().stream()
                .filter(r -> r.id().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule not loaded: " + ruleId));
        assertTrue(rule.matches(payload), () -> "rule " + ruleId + " should match: " + payload);
    }

    static Stream<Arguments> shadowedRulePayloads() {
        return Stream.of(
                arguments("exec(@cmd)", 2, "942321"), // same regex as 942320
                arguments("';", 3, "942530")          // subset of 942180
        );
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
    void urlEncodedPayloadDetectedViaTransform() {
        // The rule set itself sees the still-encoded string; the urlDecodeUni transform must expose it.
        assertTrue(RULES.firstMatch("%27%20UNION%20SELECT").isPresent(),
                "urlDecodeUni transform should expose the payload");
    }

    @Test
    void higherParanoiaLoadsMoreRules() {
        assertTrue(SqlInjectionRuleSet.loadCrsRules(2).size() > SqlInjectionRuleSet.loadCrsRules(1).size());
        assertTrue(SqlInjectionRuleSet.loadCrsRules(4).size() >= SqlInjectionRuleSet.loadCrsRules(3).size());
    }
}
