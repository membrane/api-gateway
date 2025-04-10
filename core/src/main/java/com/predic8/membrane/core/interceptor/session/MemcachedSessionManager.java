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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.util.MemcachedConnector;
import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.exception.MemcachedException;

import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * For testing, the class FakeSyncSessionStoreManager is used instead.
 */
@MCElement(name = "memcachedSessionManager")
public class MemcachedSessionManager extends SessionManager {

    private MemcachedConnector connector;
    private MemcachedClient client;
    protected String cookiePrefix = UUID.randomUUID().toString().substring(0,8);
    protected static final String ID_NAME = "_in_memory_session_id";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init(Router router) throws Exception {
        this.client = connector.getClient();
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

    protected void addSessions(Session[] sessions) {
        Arrays.stream(sessions).forEach(s -> {
            try {
                client.set(s.get(ID_NAME), Math.toIntExact(getExpiresAfterSeconds()), stringify(s));
            } catch (TimeoutException | InterruptedException | MemcachedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    protected String stringify(Session session) {
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
            try {
                client.delete(key);
            } catch (TimeoutException | InterruptedException | MemcachedException e) {
                throw new RuntimeException(e);
            }
        });
        superRemoveSession(exc);
    }

    protected void superRemoveSession(Exchange exc) {
        super.removeSession(exc);
    }


    private boolean isInvalidCookie(String cookie, String validCookie) {
        return isOwnedBySessionManager(cookie) && !cookie.contains(validCookie);
    }

    private boolean isOwnedBySessionManager(String cookie) {
        return cookie.startsWith(cookiePrefix);
    }

    protected Optional<String> getCachedSession(String cookie) {
        try {
            return Optional.ofNullable(client.get(cookie.split("=true")[0]));
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            throw new RuntimeException(e);
        }
    }

    public MemcachedConnector getConnector() {
        return connector;
    }

    @MCAttribute
    public void setConnector(MemcachedConnector connector) {
        this.connector = connector;
    }

    public String getCookiePrefix() {
        return cookiePrefix;
    }

    /**
     * Set this, if you are running multiple parallel Membrane instances.
     * @default randomly chosen 8-character long string.
     */
    @MCAttribute
    public void setCookiePrefix(String cookiePrefix) {
        this.cookiePrefix = cookiePrefix;
    }
}
