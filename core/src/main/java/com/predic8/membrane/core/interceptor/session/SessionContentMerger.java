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

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION_VALUE_SEPARATOR;

/**
 * Merges two concurrent modifications of one session against the state both of them started from.
 * <p>
 * Every request works on its own copy of the session and writes the whole copy back, so without a merge
 * the last writer silently discards everything the other one did. Comparing both sides against the state
 * as it was read turns that into a per-key decision: a key only one side touched keeps that side's value,
 * and only a key both sides changed needs a rule.
 */
class SessionContentMerger {

    private static final Pattern SEPARATOR = Pattern.compile(Pattern.quote(SESSION_VALUE_SEPARATOR));

    private SessionContentMerger() {
    }

    /**
     * @param base     the session content as this request read it
     * @param theirs   the session content as it is stored now, possibly changed by another request
     * @param ours     the session content as this request wants to store it
     * @param additive tells whether a key holds a {@link SessionManager#SESSION_VALUE_SEPARATOR}-joined
     *                 list of independent tokens; see {@link SessionManager#isAdditiveKey(String)}
     */
    static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> theirs, Map<String, Object> ours,
                                     Predicate<String> additive) {
        final Map<String, Object> merged = new HashMap<>();
        for (String key : allKeysOf(base, theirs, ours)) {
            final Object result = mergeValue(key, base.get(key), theirs.get(key), ours.get(key), additive);
            if (result != null)
                merged.put(key, result);
        }
        return merged;
    }

    private static Set<String> allKeysOf(Map<String, Object> base, Map<String, Object> theirs, Map<String, Object> ours) {
        final Set<String> keys = new HashSet<>(base.keySet());
        keys.addAll(theirs.keySet());
        keys.addAll(ours.keySet());
        return keys;
    }

    /**
     * @return the value to store, or null to leave the key out
     */
    private static Object mergeValue(String key, Object base, Object theirs, Object ours, Predicate<String> additive) {
        if (Objects.equals(ours, base))
            return theirs;  // we did not touch it, so whatever happened meanwhile stands
        if (Objects.equals(theirs, base))
            return ours;    // nobody else touched it, so our change stands
        if (Objects.equals(ours, theirs))
            return ours;    // both sides arrived at the same value
        return resolveConflict(key, base, theirs, ours, additive);
    }

    private static Object resolveConflict(String key, Object base, Object theirs, Object ours, Predicate<String> additive) {
        if (ours == null)
            return theirs;  // we removed the key, but someone changed it meanwhile: keep the newer value

        if (theirs instanceof String their && ours instanceof String our) {
            if (additive.test(key))
                return mergeTokens(base, their, our);
            if (base instanceof String common && isAppendOf(common, their) && isAppendOf(common, our))
                return mergeTokens(common, their, our);
        }

        // No rule for this key: the same last-writer-wins as before, but for this one key instead of
        // for the whole session.
        return ours;
    }

    /**
     * Both sides kept everything that was there and only added to the end - the shape
     * {@code StateManager.saveToSession} and {@code PKCEVerifier.saveToSession} produce. Recognising it
     * needs no knowledge of the key, which is why a key that is not declared additive can still be
     * merged as a list.
     */
    private static boolean isAppendOf(String base, String candidate) {
        return candidate.startsWith(base + SESSION_VALUE_SEPARATOR);
    }

    /**
     * A three-way merge of the two token lists, which is what a list-valued key needs as soon as one
     * side can also <i>remove</i> a token: {@code OAuth2CallbackRequestHandler} collapses the list to
     * the single token it just consumed. Taking the union instead would put a spent token back, and
     * taking the shrunk list as an opaque new value would drop whatever the other side added.
     * <p>
     * So a token that was already there survives only while both sides keep it - either side consuming
     * it stands - and a token neither side started with was added by one of them and survives. The
     * result is deduplicated and keeps the order the tokens were added in, because a token listed twice
     * is rejected just like a missing one: see {@code StateManager.hasExactlyOneMatchingToken}.
     *
     * @return null once nothing is left, so that the key is dropped rather than left holding ""
     */
    private static String mergeTokens(Object base, String theirs, String ours) {
        final Set<String> baseTokens = tokensOf(base instanceof String s ? s : null);
        final Set<String> theirTokens = tokensOf(theirs);
        final Set<String> ourTokens = tokensOf(ours);

        final Set<String> merged = new LinkedHashSet<>(baseTokens);
        merged.addAll(theirTokens);
        merged.addAll(ourTokens);
        merged.removeIf(token -> !survives(token, baseTokens, theirTokens, ourTokens));

        return merged.isEmpty() ? null : String.join(SESSION_VALUE_SEPARATOR, merged);
    }

    private static boolean survives(String token, Set<String> base, Set<String> theirs, Set<String> ours) {
        if (base.contains(token))
            return theirs.contains(token) && ours.contains(token);
        return theirs.contains(token) || ours.contains(token);
    }

    private static Set<String> tokensOf(String value) {
        final Set<String> tokens = new LinkedHashSet<>();
        if (value == null)
            return tokens;
        for (String token : SEPARATOR.split(value))
            if (!token.isEmpty())
                tokens.add(token);
        return tokens;
    }
}
