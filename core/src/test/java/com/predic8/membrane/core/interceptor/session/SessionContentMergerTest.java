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
package com.predic8.membrane.core.interceptor.session;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION_PARAMETER_STATE;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;

class SessionContentMergerTest {

    private static final Predicate<String> ADDITIVE = SESSION_PARAMETER_STATE::equals;

    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> theirs, Map<String, Object> ours) {
        return SessionContentMerger.merge(base, theirs, ours, ADDITIVE);
    }

    @Test
    void keyOnlyWeChangedKeepsOurValue() {
        assertEquals(Map.of("a", "ours"),
                merge(Map.of("a", "base"), Map.of("a", "base"), Map.of("a", "ours")));
    }

    @Test
    void keyOnlyTheyChangedKeepsTheirValue() {
        assertEquals(Map.of("a", "theirs"),
                merge(Map.of("a", "base"), Map.of("a", "theirs"), Map.of("a", "base")));
    }

    @Test
    void keyNeitherTouchedStaysAsItIs() {
        assertEquals(Map.of("a", "base"),
                merge(Map.of("a", "base"), Map.of("a", "base"), Map.of("a", "base")));
    }

    @Test
    void keyWeAddedIsKept() {
        assertEquals(Map.of("a", "ours"), merge(Map.of(), Map.of(), Map.of("a", "ours")));
    }

    @Test
    void keyTheyAddedIsKept() {
        assertEquals(Map.of("a", "theirs"), merge(Map.of(), Map.of("a", "theirs"), Map.of()));
    }

    @Test
    void bothAppendedToAnExistingListKeepsBothTokens() {
        assertEquals(Map.of(SESSION_PARAMETER_STATE, "t0,tTheirs,tOurs"),
                merge(Map.of(SESSION_PARAMETER_STATE, "t0"),
                        Map.of(SESSION_PARAMETER_STATE, "t0,tTheirs"),
                        Map.of(SESSION_PARAMETER_STATE, "t0,tOurs")));
    }

    /**
     * The shape is recognised from the values alone, so a key that was never declared additive is merged
     * too once it holds a list.
     */
    @Test
    void anUndeclaredKeyIsStillMergedOnceItLooksLikeAnAppend() {
        assertEquals(Map.of("undeclared", "t0,tTheirs,tOurs"),
                merge(Map.of("undeclared", "t0"),
                        Map.of("undeclared", "t0,tTheirs"),
                        Map.of("undeclared", "t0,tOurs")));
    }

    /**
     * The case an append cannot be recognised from the values: there is nothing to append to yet. This is
     * what the declared additive keys exist for - and it is the one the reported bug hits, because the
     * first two parallel logins of a session both start the list.
     */
    @Test
    void bothStartedTheListKeepsBothTokens() {
        assertEquals(Map.of(SESSION_PARAMETER_STATE, "tTheirs,tOurs"),
                merge(Map.of(),
                        Map.of(SESSION_PARAMETER_STATE, "tTheirs"),
                        Map.of(SESSION_PARAMETER_STATE, "tOurs")));
    }

    @Test
    void bothStartedANonAdditiveKeyKeepsOurs() {
        assertEquals(Map.of("undeclared", "ours"),
                merge(Map.of(), Map.of("undeclared", "theirs"), Map.of("undeclared", "ours")));
    }

    /**
     * StateManager.hasExactlyOneMatchingToken rejects a token that is listed twice just like a missing
     * one, so the union has to deduplicate.
     */
    @Test
    void aTokenBothSidesAddedIsListedOnce() {
        assertEquals(Map.of(SESSION_PARAMETER_STATE, "t0,tSame,tOurs"),
                merge(Map.of(SESSION_PARAMETER_STATE, "t0"),
                        Map.of(SESSION_PARAMETER_STATE, "t0,tSame"),
                        Map.of(SESSION_PARAMETER_STATE, "t0,tSame,tOurs")));
    }

    @Test
    void conflictingSingleValueKeepsOurs() {
        assertEquals(Map.of("a", "ours"),
                merge(Map.of("a", "base"), Map.of("a", "theirs"), Map.of("a", "ours")));
    }

    /**
     * oauth2Answer holds JSON, which contains the separator without being a list. Splitting it would
     * produce a value that no longer parses.
     */
    @Test
    void jsonValueWithCommasIsNeverSplit() {
        String theirs = "{\"access_token\":\"a\",\"expiration\":\"1\"}";
        String ours = "{\"access_token\":\"b\",\"expiration\":\"2\"}";

        assertEquals(Map.of("oauth2Answer", ours),
                merge(Map.of(), Map.of("oauth2Answer", theirs), Map.of("oauth2Answer", ours)));
    }

    /**
     * A value that happens to start with the previous one is not an append unless the separator follows,
     * or "abc" would swallow "abcdef".
     */
    @Test
    void aLongerValueIsNotAnAppendWithoutTheSeparator() {
        assertEquals(Map.of(SESSION_PARAMETER_STATE, "t0ours"),
                merge(Map.of(SESSION_PARAMETER_STATE, "t0"),
                        Map.of(SESSION_PARAMETER_STATE, "t0theirs"),
                        Map.of(SESSION_PARAMETER_STATE, "t0ours")));
    }

    @Test
    void keyWeRemovedIsRemoved() {
        assertEquals(Map.of(), merge(Map.of("a", "base"), Map.of("a", "base"), Map.of()));
    }

    @Test
    void keyWeRemovedButTheyChangedKeepsTheirValue() {
        assertEquals(Map.of("a", "theirs"), merge(Map.of("a", "base"), Map.of("a", "theirs"), Map.of()));
    }

    @Test
    void keyTheyRemovedButWeChangedKeepsOurValue() {
        assertEquals(Map.of("a", "ours"), merge(Map.of("a", "base"), Map.of(), Map.of("a", "ours")));
    }

    @Test
    void keyBothRemovedIsRemoved() {
        assertEquals(Map.of(), merge(Map.of("a", "base"), Map.of(), Map.of()));
    }

    @Test
    void untouchedKeysOfBothSidesAllSurvive() {
        Map<String, Object> base = Map.ofEntries(entry("shared", "s0"), entry("mine", "m0"), entry("theirs", "t0"));

        assertEquals(Map.ofEntries(entry("shared", "s0"), entry("mine", "m1"), entry("theirs", "t1")),
                merge(base,
                        Map.ofEntries(entry("shared", "s0"), entry("mine", "m0"), entry("theirs", "t1")),
                        Map.ofEntries(entry("shared", "s0"), entry("mine", "m1"), entry("theirs", "t0"))));
    }

    @Test
    void nonStringValuesConflictingKeepOurs() {
        assertEquals(Map.of("a", 2), merge(Map.of("a", 0), Map.of("a", 1), Map.of("a", 2)));
    }
}
