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

import com.predic8.membrane.core.router.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link RedisSessionManager} with a map where the Redis server would be, so that everything the manager
 * does around the server - key handling, the copy-on-read, the conditional-write protocol - is covered
 * without one.
 * <p>
 * What a map cannot stand in for is the {@code COMPARE_AND_SET} Lua script and the Jedis calls
 * themselves; those are only exercised against a real Redis. The map matches their contract:
 * {@code replace(key, expected, value)} is the same compare-and-set on the stored value that the script
 * performs, and {@code putIfAbsent} is {@code SET NX}.
 *
 * @see FakeSyncSessionStoreManager
 */
public class FakeSyncRedisSessionManager extends RedisSessionManager {

    private final ConcurrentHashMap<String, String> remoteContent = new ConcurrentHashMap<>();

    @Override
    public void init(Router router) {}

    @Override
    Optional<SessionCasWriter.VersionedValue> readVersioned(String key) {
        return Optional.ofNullable(remoteContent.get(key))
                .map(value -> new SessionCasWriter.VersionedValue(value, value));
    }

    @Override
    Optional<String> readAndRefreshTtl(String key) {
        return Optional.ofNullable(remoteContent.get(key));
    }

    @Override
    boolean createIfAbsent(String key, String value, int ttlSeconds) {
        return remoteContent.putIfAbsent(key, value) == null;
    }

    @Override
    boolean compareAndSet(String key, Object version, String value, int ttlSeconds) {
        return remoteContent.replace(key, (String) version, value);
    }

    @Override
    void blindSet(String key, String value, int ttlSeconds) {
        remoteContent.put(key, value);
    }

    @Override
    boolean exists(String key) {
        return remoteContent.containsKey(key);
    }

    @Override
    void delete(String key) {
        remoteContent.remove(key);
    }
}
