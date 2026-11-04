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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
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

    /**
     * RE2/J's {@code \s} does not match a vertical tab, java.util.regex's does. Rule 942160 uses a bare
     * {@code \s} without the {@code \x0b} that most CRS rules add.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "1 AND sleep\u000b(5)",
            "1+AND+sleep%0b(5)"
    })
    void verticalTabIsWhitespace(String payload) {
        var match = RULES.firstMatch(payload);
        assertTrue(match.isPresent(), () -> "should have flagged: " + payload);
        assertEquals("942160", match.get().id());
    }

    /**
     * RE2/J's {@code $} matches only at the end of the input, java.util.regex's also before a final line
     * break. Rule 942220 ends with {@code $}.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "2147483648\n",
            "2147483648\r\n",
            "2147483648%0a"
    })
    void endAnchorMatchesBeforeTrailingLineBreak(String payload) {
        var match = RULES.firstMatch(payload);
        assertTrue(match.isPresent(), () -> "should have flagged: " + payload);
        assertEquals("942220", match.get().id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a\\sb", "a\\s*b", "a\\Sb", "a[\\s]b", "a[^\\s]b", "a[^\\s\\-]b", "a[\\s\\x0b]b", "a\\\\sb", "a[\\]\\s]b",
            "a$", "^a$", "a$|b", "(?:a|b)$", "a\\$", "a[$]", "a\\\\$"
    })
    void translatedRegexMatchesLikeJavaRegex(String regex) {
        var javaPattern = java.util.regex.Pattern.compile(regex);
        var re2Pattern = SqlInjectionRuleSet.compile(regex);
        for (var input : new String[]{"ab", "a b", "a\u000bb", "a\tb", "a\nb", "a\fb", "a\rb", "axb", "a-b", "a]b", "a\\sb", "a\u00a0b",
                "a", "a\n", "a\r\n", "a\r", "a\u0085", "a\u2028", "a\u2029", "a\n\n", "a\nx", "a$", "a\\", "a\\\n"})
            assertEquals(javaPattern.matcher(input).find(), re2Pattern.matcher(input).find(),
                    () -> "regex %s on %s".formatted(regex, printable(input)));
    }

    /**
     * Every bundled regex treats vertical tabs and a trailing line break like java.util.regex, the engine
     * the CRS rules were matched with before RE2/J. Runs the payload table with every space replaced by a
     * vertical tab, and with each line break appended.
     */
    @ParameterizedTest(name = "[{index}] rule {0}")
    @MethodSource("allRuleIds")
    void everyRuleMatchesLikeJavaRegex(String ruleId) throws IOException {
        var javaPattern = java.util.regex.Pattern.compile(bundledRegex(ruleId));
        var re2Pattern = rule(ruleId).regex();
        crsRules().map(arguments -> (String) arguments.get()[0])
                .flatMap(payload -> Stream.of(payload.replace(' ', '\u000b'), payload + "\n", payload + "\r\n", payload + "\r",
                        payload + "\u0085", payload + "\u2028", payload + "\u2029"))
                .forEach(input -> assertEquals(javaPattern.matcher(input).find(), re2Pattern.matcher(input).find(),
                        () -> "rule %s on %s".formatted(ruleId, printable(input))));
    }

    private static String printable(String input) {
        return input.replace("\u000b", "\\x0b").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\u0085", "\\u0085").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    /** The regex as stored in the bundled resource, before {@link SqlInjectionRuleSet#compile} translates it. */
    private static String bundledRegex(String ruleId) throws IOException {
        try (var is = SqlInjectionRuleSet.class.getResourceAsStream("crs-sqli-rules.json")) {
            for (var rule : new ObjectMapper().readTree(is))
                if (rule.path("id").asText().equals(ruleId))
                    return rule.path("regex").asText();
        }
        throw new AssertionError("rule not bundled: " + ruleId);
    }

    /**
     * Branches of 942190 that the shared payload table does not reach, tested in isolation because
     * lower-numbered rules would match first.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "exec master.dbo.sp_who",
            "exec foo xp_cmdshell 'dir'",
            "   execute foo xp_cmdshell 'dir'",
            "select name, suser("
    })
    void rule942190MatchesItsBranches(String payload) {
        assertTrue(rule("942190").matches(payload), () -> "942190 should match: " + payload);
    }

    /**
     * Fragments that, repeated, made the backtracking engine read the input a quadratic or cubic number
     * of times (e.g. 942190 on spaces, 942251 on spaces, 942370 on {@code ' or }).
     */
    private static final String[] REPEATED_FRAGMENTS = {
            " ", "a", "'", "\"", "@", "!", "(", "' or ", "select ", "union", "execute "
    };

    /**
     * Every bundled rule must read its input a linear number of times. The reads are counted instead of
     * timed, so the test does not depend on the machine: doubling the input must at most roughly double
     * the reads.
     */
    @ParameterizedTest(name = "[{index}] rule {0}")
    @MethodSource("allRuleIds")
    void everyRuleMatchesInLinearTime(String ruleId) {
        var rule = rule(ruleId);
        for (var fragment : REPEATED_FRAGMENTS) {
            var reads = reads(rule, fragment, 4_000);
            var readsDouble = reads(rule, fragment, 8_000);
            assertTrue(readsDouble <= READ_LIMIT && readsDouble < 3 * reads,
                    () -> "rule %s reads %d chars for 4000 and %d for 8000 chars of \"%s\"".formatted(
                            ruleId, reads, readsDouble, fragment));
        }
    }

    static Stream<String> allRuleIds() {
        return BY_PARANOIA_LEVEL[4].rules().stream().map(SqlInjectionRule::id);
    }

    private static SqlInjectionRule rule(String ruleId) {
        return BY_PARANOIA_LEVEL[4].rules().stream()
                .filter(r -> r.id().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("rule not loaded: " + ruleId));
    }

    private static long reads(SqlInjectionRule rule, String fragment, int length) {
        final var input = new CountingCharSequence(fragment.repeat(length / fragment.length()));
        try {
            rule.regex().matcher(input).find();
        } catch (ReadLimitExceeded ignored) {
            // far beyond linear; a count above READ_LIMIT fails the assertion
        }
        return input.reads;
    }

    private static class ReadLimitExceeded extends RuntimeException {
    }

    private static final long READ_LIMIT = 100_000_000L;

    /** Counts {@link #charAt} calls and gives up once the count is clearly not linear. */
    private static class CountingCharSequence implements CharSequence {

        private final String value;
        private long reads;

        CountingCharSequence(String value) {
            this.value = value;
        }

        @Override
        public char charAt(int index) {
            if (++reads > READ_LIMIT)
                throw new ReadLimitExceeded();
            return value.charAt(index);
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @Test
    void higherParanoiaLoadsMoreRules() {
        assertTrue(SqlInjectionRuleSet.loadCrsRules(2).size() > SqlInjectionRuleSet.loadCrsRules(1).size());
        assertTrue(SqlInjectionRuleSet.loadCrsRules(4).size() >= SqlInjectionRuleSet.loadCrsRules(3).size());
    }
}
