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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.util.RedisConnector;
import org.springframework.beans.factory.InitializingBean;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.GetExParams;
import java.util.*;
import java.util.stream.Collectors;

@MCElement(name = "redisSessionManager")
public class RedisSessionManager extends SessionManager implements InitializingBean {
    private ObjectMapper objMapper;
    private RedisConnector connector;
    private GetExParams params;
    final static String ID_NAME = "_in_memory_session_id";

    protected String cookieNamePrefix = UUID.randomUUID().toString().substring(0,8);

    public RedisSessionManager(){
        objMapper = new ObjectMapper();
    }

    @Override
    public void init(Router router) throws Exception {

    }


    @Override
    public void afterPropertiesSet() throws Exception {

    }

    @Override
    protected Map<String, Object> cookieValueToAttributes(String cookie) {
        try(Jedis jedis = connector.getJedisWithDb()){

            return  (!jedis.get(cookie.split("=true")[0]).equals("nil")) ?
                    jsonStringtoSession(jedis.getEx(cookie.split("=true")[0], connector.getParams())).get() : new Session(usernameKeyName, new HashMap<>()).get();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
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
        try(Jedis jedis = connector.getJedisWithDb()){
            Arrays.stream(session).forEach(s -> {
                try {
                    jedis.setex(s.get(ID_NAME), getExpiresAfterSeconds(), sessionToJsonString(s));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void createSessionIdsForNewSessions(Session[] session) {
        Arrays.stream(session).filter(s -> s.get(ID_NAME) == null).forEach(s -> s.put(ID_NAME, cookieNamePrefix + "-" +UUID.randomUUID().toString()));
    }

    private void fixMergedSessionId(Session[] session) {
        Arrays.stream(session)
                .filter(s -> s.get(ID_NAME).toString().contains(","))
                .forEach(s -> s.put(ID_NAME, cookieNamePrefix + "-" +UUID.randomUUID().toString()));
    }

    private String sessionToJsonString(Session session) throws JsonProcessingException {
        return objMapper.writeValueAsString(session);
    }

    private Session jsonStringtoSession(String session) throws JsonProcessingException {
        return objMapper.readValue(session, Session.class);
    }

    @Override
    public List<String> getInvalidCookies(Exchange exc, String validCookie) {
        List<HeaderField> values = exc.getRequest().getHeader().getValues(new HeaderName(Header.COOKIE));
        return values.stream()
                .filter(hf -> hf.getValue().startsWith(cookieNamePrefix))
                .filter(hf -> !hf.getValue().contains(validCookie)).map(HeaderField::getValue)
                .collect(Collectors.toList());
    }

    @Override
    protected boolean isValidCookieForThisSessionManager(String cookie) {
        try(Jedis jedis = connector.getJedisWithDb()){
            return cookie.startsWith(cookieNamePrefix) && !jedis.get(cookie.split("=true")[0]).equals("nil");
        }
    }

    @Override
    protected boolean cookieRenewalNeeded(String originalCookie) {
        try(Jedis jedis = connector.getJedisWithDb()){
            return !jedis.get(originalCookie).equals("nil");
        }
    }

    public RedisConnector getConnector() {
        return connector;
    }

    @MCAttribute
    public void setConnector(RedisConnector connector) {
        this.connector = connector;
    }
}
