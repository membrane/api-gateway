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
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import redis.clients.jedis.*;

import java.util.*;
import java.util.stream.*;

@MCElement(name = "redisSessionManager")
public class RedisSessionManager extends SessionManager{

    private static final Logger log = LoggerFactory.getLogger(RedisSessionManager.class);

    protected String cookieNamePrefix = UUID.randomUUID().toString().substring(0,8);
    private final ObjectMapper objMapper;
    private RedisConnector connector;
    static final String ID_NAME = "_in_memory_session_id";


    public RedisSessionManager(){
        objMapper = new ObjectMapper();
    }


    @Override
    public void init(Router router) throws Exception {
        //Nothing to do
    }

    @Override
    protected Map<String, Object> cookieValueToAttributes(String cookie) {
        try {
            try (Jedis jedis = connector.getJedisWithDb()) {
                return (!jedis.get(cookie.split("=true")[0]).equals("nil")) ?
                        jsonStringtoSession(jedis.getEx(cookie.split("=true")[0], connector.getParams())).get() : new Session(usernameKeyName, new HashMap<>()).get();
            }
        } catch (JsonProcessingException e) {
            log.debug("Cannot parse JSON in Cookie.",e);
        }
        return Collections.emptyMap();
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
        Arrays.stream(session).forEach(s -> {
            try {
                try (Jedis jedis = connector.getJedisWithDb()) {
                    jedis.setex(s.get(ID_NAME), getExpiresAfterSeconds(), sessionToJsonString(s));
                }
            } catch (JsonProcessingException e) {
                log.debug("Cannot process JSON.",e);
            }
        });
    }

    private void createSessionIdsForNewSessions(Session[] session) {
        Arrays.stream(session).filter(s -> s.get(ID_NAME) == null).forEach(s -> s.put(ID_NAME, cookieNamePrefix + "-" +UUID.randomUUID()));
    }

    private void fixMergedSessionId(Session[] session) {
        Arrays.stream(session)
                .filter(s -> s.get(ID_NAME).toString().contains(","))
                .forEach(s -> s.put(ID_NAME, cookieNamePrefix + "-" +UUID.randomUUID()));
    }

    private String sessionToJsonString(Session session) throws JsonProcessingException {
        return objMapper.writeValueAsString(session);
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
                .filter(value -> value.startsWith(cookieNamePrefix)).filter(value -> !value.contains(validCookie))
                .toList();
    }

    @Override
    protected boolean isValidCookieForThisSessionManager(String cookie) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            return cookie.startsWith(cookieNamePrefix) && !jedis.get(cookie.split("=true")[0]).equals("nil");
        }
    }

    @Override
    protected boolean cookieRenewalNeeded(String originalCookie) {
        try (Jedis jedis = connector.getJedisWithDb()) {
            return !jedis.get(originalCookie).equals("nil");
        }
    }

    @Override
    public void removeSession(Exchange exc) {
        getInvalidCookies(exc, UUID.randomUUID().toString()).forEach(key -> {
            try (Jedis jedis = connector.getJedisWithDb()) {
                jedis.del(key);
            }
        });
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
}
