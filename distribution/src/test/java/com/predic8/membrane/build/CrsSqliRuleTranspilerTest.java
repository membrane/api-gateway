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

package com.predic8.membrane.build;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class CrsSqliRuleTranspilerTest {

    @Test
    void transpilesRxRule() {
        final var rules = CrsSqliRuleTranspiler.transpile(rule("(?i)union[\\s\\x0b]+select"));

        assertEquals(1, rules.size());
        final var rule = rules.get(0);
        assertEquals("942999", rule.get("id").asText());
        assertEquals(2, rule.get("paranoiaLevel").asInt());
        assertEquals("urlDecodeUni", rule.get("transforms").get(0).asText());
        assertEquals("Test rule", rule.get("message").asText());
        assertEquals("(?i)union[\\s\\x0b]+select", rule.get("regex").asText());
    }

    /**
     * A Windows checkout with core.autocrlf=true has CRLF line endings; line continuations must still be joined.
     */
    @Test
    void crlfLineEndingsGiveSameRulesAsLf() {
        final var lf = rule("(?i)union[\\s\\x0b]+select");
        assertEquals(CrsSqliRuleTranspiler.transpile(lf), CrsSqliRuleTranspiler.transpile(lf.replace("\n", "\r\n")));
    }

    @Test
    void rejectsRegexNotSupportedByRe2() {
        final var e = assertThrows(IllegalStateException.class,
                () -> CrsSqliRuleTranspiler.transpile(rule("union(?=\\s)")));
        assertTrue(e.getMessage().contains("942999"), e.getMessage());
    }

    @Test
    void bundledCrsFileIsSupportedByRe2() throws IOException {
        final var conf = Files.readString(Path.of("crs-sqli-rules/REQUEST-942-APPLICATION-ATTACK-SQLI.conf"), UTF_8);
        assertFalse(CrsSqliRuleTranspiler.transpile(conf).isEmpty());
    }

    private static String rule(String regex) {
        return """
                SecRule ARGS "@rx %s" \\
                    "id:942999,\\
                    phase:2,\\
                    t:none,t:urlDecodeUni,\\
                    msg:'Test rule',\\
                    tag:'paranoia-level/2'"
                """.formatted(regex);
    }
}
