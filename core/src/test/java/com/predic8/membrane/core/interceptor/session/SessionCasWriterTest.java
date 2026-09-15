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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionCasWriterTest {

    private static final int TTL = 42;

    private FakeStore store;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
    }

    @Test
    void unconditionalWriteUpdatesBaseSnapshot() {
        store.stored = serialize(Map.of("a", "1"));
        Session session = sessionWith(Map.of("a", "1"), Map.of("a", "2"));

        SessionCasWriter.write(store, "key", session, TTL);

        assertEquals(Map.of("a", "2"), store.parse(store.stored));
        assertEquals(TTL, store.ttlSeconds);
        assertEquals(Map.of("a", "2"), session.getBaseSnapshot());
    }

    /**
     * With the snapshot left stale, the next write would read the key it just stored as one another
     * request had added, and put the removed key back.
     */
    @Test
    void removalAfterUnconditionalWriteIsNotUndone() {
        store.stored = serialize(Map.of("a", "1"));
        Session session = sessionWith(Map.of("a", "1"), Map.of("a", "2"));
        SessionCasWriter.write(store, "key", session, TTL);

        store.compareAndSetSucceeds = true;
        session.remove("a");
        SessionCasWriter.write(store, "key", session, TTL);

        assertEquals(Map.of(), store.parse(store.stored));
    }

    private static Session sessionWith(Map<String, Object> base, Map<String, Object> content) {
        Session session = new Session();
        session.setContent(content);
        session.setBaseSnapshot(base);
        return session;
    }

    private static String serialize(Map<String, Object> content) {
        return new TreeMap<>(content).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(joining(";"));
    }

    /**
     * Rejects every conditional write until told otherwise, which is what drives {@link SessionCasWriter}
     * into its unconditional fallback.
     */
    private static class FakeStore implements SessionCasWriter.Store {

        String stored;
        int ttlSeconds;
        boolean compareAndSetSucceeds;

        @Override
        public Optional<SessionCasWriter.VersionedValue> read(String key) {
            return Optional.ofNullable(stored).map(value -> new SessionCasWriter.VersionedValue(value, value));
        }

        @Override
        public boolean createIfAbsent(String key, String value, int ttlSeconds) {
            if (stored != null)
                return false;
            set(value, ttlSeconds);
            return true;
        }

        @Override
        public boolean compareAndSet(String key, Object version, String value, int ttlSeconds) {
            if (!compareAndSetSucceeds || !stored.equals(version))
                return false;
            set(value, ttlSeconds);
            return true;
        }

        @Override
        public void blindSet(String key, String value, int ttlSeconds) {
            set(value, ttlSeconds);
        }

        @Override
        public Map<String, Object> parse(String value) {
            if (value.isEmpty())
                return Map.of();
            return Arrays.stream(value.split(";"))
                    .map(entry -> entry.split("=", 2))
                    .collect(toMap(entry -> entry[0], entry -> entry[1]));
        }

        @Override
        public String serialize(Map<String, Object> content) {
            return SessionCasWriterTest.serialize(content);
        }

        @Override
        public boolean isAdditiveKey(String key) {
            return false;
        }

        private void set(String value, int ttl) {
            stored = value;
            ttlSeconds = ttl;
        }
    }
}
