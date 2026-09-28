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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * The half of {@link SessionCasWriter.Store} that does not depend on where the bytes are kept: a stored
 * session is the JSON of a {@link Session}, and which keys merge additively is the session manager's
 * decision. What is left for a manager to implement are the four operations that talk to its server.
 */
abstract class SessionJsonStore implements SessionCasWriter.Store {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SessionManager manager;

    SessionJsonStore(SessionManager manager) {
        this.manager = manager;
    }

    @Override
    public Map<String, Object> parse(String value) {
        try {
            return MAPPER.readValue(value, Session.class).getContent();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot parse stored session.", e);
        }
    }

    @Override
    public String serialize(Map<String, Object> content) {
        try {
            return MAPPER.writeValueAsString(manager.rawSession(content));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot serialize session.", e);
        }
    }

    @Override
    public boolean isAdditiveKey(String key) {
        return manager.isAdditiveKey(key);
    }
}
