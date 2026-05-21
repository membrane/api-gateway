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

package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.core.mcp.MCPInitialize;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class McpSessionManager {

    private static final long DEFAULT_SESSION_TTL_MILLIS = Duration.ofHours(1).toMillis();

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final long sessionTtlMillis;
    private final LongSupplier currentTimeMillis;

    McpSessionManager() {
        this(DEFAULT_SESSION_TTL_MILLIS, System::currentTimeMillis);
    }

    McpSessionManager(long sessionTtlMillis, LongSupplier currentTimeMillis) {
        this.sessionTtlMillis = sessionTtlMillis;
        this.currentTimeMillis = currentTimeMillis;
    }

    String createSession(MCPInitialize initialize, String protocolVersion) {
        long now = now();
        cleanupExpiredSessions(now);

        String sessionId = UUID.randomUUID().toString();
        McpSessionContext context = new McpSessionContext();
        if (!context.initialize(protocolVersion, initialize.getClientInfo())) {
            throw new IllegalStateException("'initialize' must be the first MCP request");
        }
        sessions.put(sessionId, new SessionEntry(context, now));
        return sessionId;
    }

    @Nullable
    McpSessionContext get(@Nullable String sessionId) {
        long now = now();
        cleanupExpiredSessions(now);

        if (sessionId == null) {
            return null;
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry == null) {
            return null;
        }
        entry.touch(now);
        return entry.context();
    }

    private void cleanupExpiredSessions(long now) {
        long oldestAllowedAccess = now - sessionTtlMillis;
        sessions.entrySet().removeIf(entry -> entry.getValue().lastAccessedAt() < oldestAllowedAccess);
    }

    private long now() {
        return currentTimeMillis.getAsLong();
    }

    private static final class SessionEntry {
        private final McpSessionContext context;
        private volatile long lastAccessedAt;

        private SessionEntry(McpSessionContext context, long lastAccessedAt) {
            this.context = context;
            this.lastAccessedAt = lastAccessedAt;
        }

        private McpSessionContext context() {
            return context;
        }

        private long lastAccessedAt() {
            return lastAccessedAt;
        }

        private void touch(long lastAccessedAt) {
            this.lastAccessedAt = lastAccessedAt;
        }
    }
}
