/* Copyright 2024 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.router.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class emulates systems like Redis or MemcacheD.
 * <p>
 * It replaces only where the bytes are kept, so the copy-on-read and conditional-write semantics a real
 * deployment has are preserved. The stored value doubles as the version: comparing it is exactly what
 * memcached's CAS token achieves, apart from an ABA that cannot happen here because every write of a
 * session differs from the last.
 */
public class FakeSyncSessionStoreManager extends MemcachedSessionManager {

    private final ConcurrentHashMap<String, String> remoteContent = new ConcurrentHashMap<>();

    @Override
    public void init(Router router) throws Exception {}

    @Override
    protected Optional<SessionCasWriter.VersionedValue> readVersioned(String key) {
        return Optional.ofNullable(remoteContent.get(key))
                .map(value -> new SessionCasWriter.VersionedValue(value, value));
    }

    @Override
    protected boolean createIfAbsent(String key, String value, int ttlSeconds) {
        return remoteContent.putIfAbsent(key, value) == null;
    }

    @Override
    protected boolean compareAndSet(String key, Object version, String value, int ttlSeconds) {
        return remoteContent.replace(key, (String) version, value);
    }

    @Override
    protected void blindSet(String key, String value, int ttlSeconds) {
        remoteContent.put(key, value);
    }

    @Override
    public void removeSession(Exchange exc) {
        getInvalidCookies(exc, UUID.randomUUID().toString()).forEach(remoteContent::remove);
        super.superRemoveSession(exc);
    }
}
