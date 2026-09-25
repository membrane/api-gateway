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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Writes a session back to a store that several Membrane instances share, without discarding what another
 * request wrote while this one was running.
 * <p>
 * The store is only able to replace a whole serialized session, so two requests that both read {@code X},
 * changed their own copy and wrote it back leave only the second one's version behind. Reading again right
 * before writing does not help - the same window just gets smaller. Instead the write is made conditional:
 * it only lands if the stored session is still the one that was merged against, and otherwise the merge is
 * redone against what is there now.
 *
 * @see SessionContentMerger
 */
class SessionCasWriter {

    private static final Logger log = LoggerFactory.getLogger(SessionCasWriter.class);

    /**
     * An attempt is only spent when another request stored the session in the meantime, so the bound has
     * to cover the number of requests writing one session at once - not the number of requests in flight.
     * Two hundred parallel writers on a single session stay below this.
     */
    private static final int MAX_ATTEMPTS = 50;

    /**
     * The first few collisions are retried straight away: with two or three writers one of them wins
     * immediately and a delay would only slow the response down.
     */
    private static final int ATTEMPTS_BEFORE_BACKOFF = 3;

    /**
     * Keeps the worst case - every attempt colliding - at well under a second, which is what a request
     * blocked in here costs.
     */
    private static final int MAX_BACKOFF_MILLIS = 16;

    private SessionCasWriter() {
    }

    /**
     * A stored session together with whatever the store needs in order to write it back conditionally -
     * a CAS token for memcached, the stored value itself for Redis.
     */
    record VersionedValue(String value, Object version) {
    }

    /**
     * The operations a store has to offer for {@link #write}. Implemented per session manager; all values
     * are the serialized form of a session.
     */
    interface Store {

        Optional<VersionedValue> read(String key);

        /**
         * @return false if the key already exists, in which case nothing was written
         */
        boolean createIfAbsent(String key, String value, int ttlSeconds);

        /**
         * @return false if the stored value no longer matches {@code version}, in which case nothing was written
         */
        boolean compareAndSet(String key, Object version, String value, int ttlSeconds);

        /**
         * Replaces whatever is stored. Only used once retrying has been given up on.
         */
        void blindSet(String key, String value, int ttlSeconds);

        Map<String, Object> parse(String value);

        String serialize(Map<String, Object> content);

        boolean isAdditiveKey(String key);
    }

    /**
     * Stores {@code session} under {@code key}, merged with any concurrent change. On success the session
     * is updated to the merged content, so that the rest of the response sees what was actually stored.
     */
    static void write(Store store, String key, Session session, int ttlSeconds) {
        final Map<String, Object> base = session.getBaseSnapshot();
        final Map<String, Object> ours = Map.copyOf(session.getContent());
        final String serializedOurs = store.serialize(ours);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final Optional<VersionedValue> stored = store.read(key);

            if (stored.isEmpty()) {
                // Nothing to merge with: a new session, a session id regenerated after a cookie merge or
                // after authorize(), or an entry that expired while the request ran.
                if (store.createIfAbsent(key, serializedOurs, ttlSeconds)) {
                    session.setBaseSnapshot(ours);
                    return;
                }
                continue;
            }

            final Map<String, Object> merged = mergeWithStored(store, base, ours, stored.get());

            if (store.compareAndSet(key, stored.get().version(), store.serialize(merged), ttlSeconds)) {
                session.setContent(merged);
                // What was just stored is what the next write has to merge against. A request writes its
                // session twice - once from postProcess on the request, once on the response - and without
                // this the second write would present another request's absorbed changes as its own.
                session.setBaseSnapshot(merged);
                return;
            }

            backOff(attempt);
        }

        log.warn("Could not store session {} without conflict after {} attempts; " +
                 "writing it unconditionally, which may discard a concurrent change.", key, MAX_ATTEMPTS);
        // Still merged with what is stored now: giving up on the retry means giving up on the guarantee
        // that nothing written in the last moment is lost, not on everything the session has collected
        // so far. Writing our own copy over it would drop every change since this request read it.
        final Map<String, Object> lastResort = store.read(key)
                .map(stored -> mergeWithStored(store, base, ours, stored))
                .orElse(ours);
        store.blindSet(key, store.serialize(lastResort), ttlSeconds);
        session.setContent(lastResort);
        session.setBaseSnapshot(lastResort);
    }

    private static Map<String, Object> mergeWithStored(Store store, Map<String, Object> base,
                                                       Map<String, Object> ours, VersionedValue stored) {
        return SessionContentMerger.merge(base, store.parse(stored.value()), ours, store::isAdditiveKey);
    }

    /**
     * Without a delay every writer that lost a round re-reads and re-writes in lockstep with the others,
     * so they keep colliding and each round trip is spent on the store for nothing. The delay is randomized
     * to break exactly that lockstep, and grows so that heavier contention is spread wider.
     */
    private static void backOff(int attempt) {
        if (attempt < ATTEMPTS_BEFORE_BACKOFF)
            return;
        long window = Math.min(1L << (attempt - ATTEMPTS_BEFORE_BACKOFF + 1), MAX_BACKOFF_MILLIS);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(window) + 1);
        } catch (InterruptedException e) {
            // Retry right away instead of failing the write; the flag stays set for whoever is shutting
            // this thread down.
            Thread.currentThread().interrupt();
        }
    }
}
