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
     * Enough attempts that losing all of them means the session is contended far beyond what retrying
     * would fix.
     */
    private static final int MAX_ATTEMPTS = 10;

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

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final Optional<VersionedValue> stored = store.read(key);

            if (stored.isEmpty()) {
                // Nothing to merge with: a new session, a session id regenerated after a cookie merge or
                // after authorize(), or an entry that expired while the request ran.
                if (store.createIfAbsent(key, store.serialize(ours), ttlSeconds)) {
                    session.setBaseSnapshot(ours);
                    return;
                }
                continue;
            }

            final Map<String, Object> merged =
                    SessionContentMerger.merge(base, store.parse(stored.get().value()), ours, store::isAdditiveKey);

            if (store.compareAndSet(key, stored.get().version(), store.serialize(merged), ttlSeconds)) {
                session.setContent(merged);
                // What was just stored is what the next write has to merge against. A request writes its
                // session twice - once from postProcess on the request, once on the response - and without
                // this the second write would present another request's absorbed changes as its own.
                session.setBaseSnapshot(merged);
                return;
            }
        }

        log.warn("Could not store session {} without conflict after {} attempts; " +
                 "writing it unconditionally, which may discard a concurrent change.", key, MAX_ATTEMPTS);
        store.blindSet(key, store.serialize(ours), ttlSeconds);
    }
}
