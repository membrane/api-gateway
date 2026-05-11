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

    boolean markReady(@Nullable String sessionId) {
        long now = now();
        cleanupExpiredSessions(now);

        if (sessionId == null) {
            return false;
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry == null) {
            return false;
        }
        entry.touch(now);
        return entry.context().markReady();
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
