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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class emulates systems like Redis or MemcacheD.
 */
public class FakeSyncSessionStoreManager extends MemcachedSessionManager {

    private final ConcurrentHashMap<String, String> remoteContent = new ConcurrentHashMap<>();

    @Override
    public void init(Router router) throws Exception {}

    @Override
    protected void addSessions(Session[] sessions) {
        Arrays.stream(sessions).forEach(s -> remoteContent.put(s.get(ID_NAME), stringify(s)));
    }

    @Override
    public void removeSession(Exchange exc) {
        getInvalidCookies(exc, UUID.randomUUID().toString()).forEach(remoteContent::remove);
        super.superRemoveSession(exc);
    }

    @Override
    protected Optional<String> getCachedSession(String cookie) {
        return Optional.ofNullable(remoteContent.get(cookie.split("=true")[0]));
    }
}
