package com.predic8.membrane.core.interceptor.oauth2.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.interceptor.session.SessionManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class emulates systems like Redis or MemcacheD.
 */
public class FakeSyncSessionStoreManager extends SessionManager {

    protected String cookiePrefix = UUID.randomUUID().toString().substring(0,8);
    private static final String ID_NAME = "_in_memory_session_id";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConcurrentHashMap<String, String> remoteContent = new ConcurrentHashMap<>();

    @Override
    public void init(Router router) throws Exception {
    }

    @Override
    protected Map<String, Object> cookieValueToAttributes(String cookie) {
        return getCachedSession(cookie)
                .map(this::parse)
                .orElse(new Session(usernameKeyName, new HashMap<>()))
                .get();
    }

    private Session parse(String json) {
        try {
            return objectMapper.readValue(json, Session.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Map<Session, String> getCookieValues(Session... session) {
        createNewSessions(session);
        fixMergedSessionId(session);

        addSessions(session);

        return Arrays.stream(session)
                .collect(Collectors.toMap(Function.identity(), s -> s.get(ID_NAME)));
    }

    private void addSessions(Session[] sessions) {
        Arrays.stream(sessions).forEach(s -> {
            remoteContent.put(s.get(ID_NAME), stringify(s));
        });
    }

    private String stringify(Session session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void fixMergedSessionId(Session[] sessions) {
        Arrays.stream(sessions)
                .filter(s -> s.get(ID_NAME).toString().contains(","))
                .forEach(s -> s.put(ID_NAME, getNewSessionId()));
    }

    private void createNewSessions(Session[] sessions) {
        Arrays.stream(sessions)
                .filter(this::isNewSession)
                .forEach(this::createSessionId);
    }

    private void createSessionId(Session session) {
        session.put(ID_NAME, getNewSessionId());
    }

    private String getNewSessionId() {
        return cookiePrefix + "-" + UUID.randomUUID();
    }

    private boolean isNewSession(Session session) {
        return session.get(ID_NAME) == null;
    }

    @Override
    public List<String> getInvalidCookies(Exchange exc, String validCookie) {
        return getCookieHeaderFields(exc).stream()
                .map(HeaderField::getValue)
                .flatMap(s -> Arrays.stream(s.split(";")))
                .map(String::trim)
                .filter(value -> isInvalidCookie(value, validCookie))
                .toList();
    }

    @Override
    protected boolean isValidCookieForThisSessionManager(String cookie) {
        return isOwnedBySessionManager(cookie) && cookieRenewalNeeded(cookie);
    }

    @Override
    protected boolean cookieRenewalNeeded(String originalCookie) {
        return getCachedSession(originalCookie).isPresent();
    }

    @Override
    public void removeSession(Exchange exc) {
        getInvalidCookies(exc, UUID.randomUUID().toString()).forEach(key -> {
            remoteContent.remove(key);
        });
        super.removeSession(exc);
    }


    private boolean isInvalidCookie(String cookie, String validCookie) {
        return isOwnedBySessionManager(cookie) && !cookie.contains(validCookie);
    }

    private boolean isOwnedBySessionManager(String cookie) {
        return cookie.startsWith(cookiePrefix);
    }

    private Optional<String> getCachedSession(String cookie) {
        return Optional.ofNullable(remoteContent.get(cookie.split("=true")[0]));
    }

}
