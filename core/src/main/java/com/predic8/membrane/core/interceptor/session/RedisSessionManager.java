/* Copyright 2021 predic8 GmbH, www.predic8.com

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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import redis.clients.jedis.*;
import redis.clients.jedis.params.*;

import java.util.*;
import java.util.stream.*;

/**
 * For testing, the class FakeSyncRedisSessionManager is used instead.
 */
@MCElement(name = "redisSessionManager")
public class RedisSessionManager extends SessionManager{

    private static final Logger log = LoggerFactory.getLogger(RedisSessionManager.class);

    protected String cookieNamePrefix = UUID.randomUUID().toString().substring(0,8);
    private final ObjectMapper objMapper;
    private RedisConnector connector;
    static final String ID_NAME = "_in_memory_session_id";

    /**
     * Replaces the stored session only if it is still the one that was read, so that a concurrent write
     * is merged instead of overwritten. Redis hands out no version token, so the stored value itself
     * serves as one; every write of a session differs from the last, so an ABA cannot occur.
     */
    private static final String COMPARE_AND_SET =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1 " +
            "else return 0 end";

    public RedisSessionManager(){
        objMapper = new ObjectMapper();
    }


    @Override
    public void init(Router router) throws Exception {
        //Nothing to do
    }

    @Override
    protected Map<String, Object> cookieValueToAttributes(String cookie) {
        return getCachedSession(cookie)
                .map(this::parse)
                .orElse(new Session(usernameKeyName, new HashMap<>()))
                .get();
    }

    private Optional<String> getCachedSession(String cookie) {
        return readAndRefreshTtl(getKeyOfCookie(cookie));
    }

    private Session parse(String json) {
        try {
            return jsonStringtoSession(json);
        } catch (JsonProcessingException e) {
            log.debug("Cannot parse JSON in Cookie.", e);
            return new Session(usernameKeyName, new HashMap<>());
        }
    }

    @Override
    protected Map<Session, String> getCookieValues(Session... session) {
        createSessionIdsForNewSessions(session);
        fixMergedSessionId(session);
        addSessionToRedis(session);
        return mapSessionToName(session);
    }

    private Map<Session, String> mapSessionToName(Session[] session) {
        return Arrays.stream(session)
                .collect(Collectors.toMap(s -> s, s -> s.get(ID_NAME)));
    }

    private void addSessionToRedis(Session[] session) {
        Arrays.stream(session).forEach(s ->
                SessionCasWriter.write(store, s.get(ID_NAME), s, Math.toIntExact(getExpiresAfterSeconds())));
    }

    /**
     * Adapts Redis to what {@link SessionCasWriter} needs. Every operation that talks to the server is a
     * separate method so that a test double can replace the server without reimplementing anything else.
     */
    private final SessionCasWriter.Store store = new SessionJsonStore(this) {
        @Override
        public Optional<SessionCasWriter.VersionedValue> read(String key) {
            return readVersioned(key);
        }

        @Override
        public boolean createIfAbsent(String key, String value, int ttlSeconds) {
            return RedisSessionManager.this.createIfAbsent(key, value, ttlSeconds);
        }

        @Override
        public boolean compareAndSet(String key, Object version, String value, int ttlSeconds) {
            return RedisSessionManager.this.compareAndSet(key, version, value, ttlSeconds);
        }

        @Override
        public void blindSet(String key, String value, int ttlSeconds) {
            RedisSessionManager.this.blindSet(key, value, ttlSeconds);
        }
    };

    /**
     * Redis hands out no version token, so the stored value itself serves as one; see
     * {@link #COMPARE_AND_SET}.
     */
    Optional<SessionCasWriter.VersionedValue> readVersioned(String key) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            return Optional.ofNullable(jedis.get(key))
                    .map(value -> new SessionCasWriter.VersionedValue(value, value));
        }
    }

    boolean createIfAbsent(String key, String value, int ttlSeconds) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            return jedis.set(key, value, SetParams.setParams().nx().ex(ttlSeconds)) != null;
        }
    }

    boolean compareAndSet(String key, Object version, String value, int ttlSeconds) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            Object applied = jedis.eval(COMPARE_AND_SET, List.of(key),
                    List.of((String) version, value, Integer.toString(ttlSeconds)));
            return Long.valueOf(1).equals(applied);
        }
    }

    void blindSet(String key, String value, int ttlSeconds) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            jedis.setex(key, ttlSeconds, value);
        }
    }

    /**
     * Reads a session and refreshes its expiry, which is what an accessed session needs and what
     * {@link #readVersioned} deliberately does not do.
     */
    Optional<String> readAndRefreshTtl(String key) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            return Optional.ofNullable(jedis.getEx(key, connector.getParams()));
        }
    }

    boolean exists(String key) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            return jedis.exists(key);
        }
    }

    void delete(String key) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            jedis.del(key);
        }
    }

    private void createSessionIdsForNewSessions(Session[] session) {
        Arrays.stream(session).filter(s -> s.get(ID_NAME) == null).forEach(s -> s.put(ID_NAME, cookieNamePrefix + "-" +UUID.randomUUID()));
    }

    private void fixMergedSessionId(Session[] session) {
        Arrays.stream(session)
                .filter(s -> s.get(ID_NAME).toString().contains(","))
                .forEach(s -> s.put(ID_NAME, cookieNamePrefix + "-" +UUID.randomUUID()));
    }

    private Session jsonStringtoSession(String session) throws JsonProcessingException {
        return objMapper.readValue(session, Session.class);
    }

    @Override
    public List<String> getInvalidCookies(Exchange exc, String validCookie) {
        return getCookieHeaderFields(exc).stream()
                .map(HeaderField::getValue)
                .flatMap(s -> Arrays.stream(s.split(";")))
                .map(String::trim)
                .map(this::getKeyOfCookie)
                .filter(value -> value.startsWith(cookieNamePrefix)).filter(value -> !value.contains(validCookie))
                .toList();
    }

    @Override
    protected boolean isValidCookieForThisSessionManager(String cookie) {
        return cookie.startsWith(cookieNamePrefix) && isStored(cookie);
    }

    @Override
    protected boolean cookieRenewalNeeded(String originalCookie) {
        return isStored(originalCookie);
    }

    private boolean isStored(String cookie) {
        return exists(getKeyOfCookie(cookie));
    }

    @Override
    public void removeSession(Exchange exc) {
        getInvalidCookies(exc, UUID.randomUUID().toString()).forEach(this::delete);
        super.removeSession(exc);
    }

    @SuppressWarnings("unused")
    public RedisConnector getConnector() {
        return connector;
    }

    @MCAttribute
    public void setConnector(RedisConnector connector) {
        this.connector = connector;
    }

    public String getCookiePrefix() {
        return cookieNamePrefix;
    }

    /**
     * Set this, if you are running multiple parallel Membrane instances.
     * @default randomly chosen 8-character long string.
     */
    @MCAttribute
    public void setCookiePrefix(String cookiePrefix) {
        this.cookieNamePrefix = cookiePrefix;
    }

}
