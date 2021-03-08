package com.predic8.membrane.core.interceptor.session.inmemory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.interceptor.session.SessionManager;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@MCElement(name = "inMemorySessionManager2")
public class InMemorySessionManager extends SessionManager {

    final static String ID_NAME = "_in_memory_session_id";
    Duration sessionExpiration = Duration.ofMinutes(30);
    Cache<String, Session> sessions;

    @Override
    public void init(Router router) throws Exception {
        sessions = CacheBuilder.newBuilder()
                .expireAfterAccess(sessionExpiration)
                .build();
    }

    @Override
    protected Map<String, Object> cookieValueToAttributes(String cookie) {
        try {
            synchronized (sessions) {
                return sessions.get(cookie.split("=true")[0], () -> new Session(usernameKeyName, new HashMap<>())).get();
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Map<Session, String> getCookieValues(Session... session) {
        Arrays.stream(session).filter(s -> s.get(ID_NAME) == null).forEach(s -> s.put(ID_NAME, UUID.randomUUID().toString()));

        synchronized (sessions) {
            Arrays.stream(session).forEach(s -> sessions.put(s.get(ID_NAME), s));
        }

        return Arrays.stream(session)
                .collect(Collectors.toMap(s -> s, s -> s.get(ID_NAME)));
    }

    @Override
    public List<String> getInvalidCookies(Exchange exc, String validCookie) {
        return Arrays.asList();
    }

    @Override
    protected boolean isValidCookieForThisSessionManager(String cookie) {
        synchronized (sessions) {
            return sessions.getIfPresent(cookie.split("=true")[0]) != null;
        }
    }

    @Override
    protected boolean cookieRenewalNeeded(String originalCookie) {
        synchronized (sessions) {
            return sessions.getIfPresent(originalCookie) == null;
        }
    }
}
