/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.interceptor.session.Session;
import org.springframework.beans.factory.InitializingBean;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.io.IOException;

//ask till about postprocess method maybe make it interface and check if instance or maybe another gucci way?
/**
 * @description  Used for storing exchanges temporarily in Redis. Supports authentication with or without password and username
 */
@MCElement(name = "redisOriginalExchangeStore")
public class RedisOriginalExchangeStore extends OriginalExchangeStore implements InitializingBean {
    private JedisPool pool;
    private String host = "localhost";
    private int port = 6379;
    private int dbNumber = 0;
    private int maxBodySize = 100000;
    private int timeout = 10000;
    private boolean ssl = false;
    private String prefix;
    private String user;
    private String password;
    private ObjectMapper objMapper;


    public RedisOriginalExchangeStore(){
        objMapper = new ObjectMapper();
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        if(user == null && password != null){
            pool = new JedisPool(new JedisPoolConfig(), host, port, timeout, password, ssl);
        }
        else if(user != null && password != null){
            pool = new JedisPool(new JedisPoolConfig(), host, port, timeout, user, password, ssl);
        }
        else{
            pool = new JedisPool(new JedisPoolConfig(), host, port, ssl);
        }
    }

    private String originalRequestKeyNameInSession(String state) {
        return prefix != null ? prefix + state : state;
    }

    @Override
    public void store(Exchange exchange, Session session, String state, Exchange exchangeToStore) throws IOException {
        try(Jedis jedis = getJedisWithDb()){
            jedis.setex(originalRequestKeyNameInSession(state), 3600, objMapper.writeValueAsString(getTrimmedAbstractExchangeSnapshot(exchangeToStore, maxBodySize)) );
        }
    }

    @Override
    public AbstractExchangeSnapshot reconstruct(Exchange exchange, Session session, String state) {
        try(Jedis jedis = getJedisWithDb()){
            return objMapper.readValue(jedis.get(originalRequestKeyNameInSession(state)), AbstractExchangeSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void remove(Exchange exchange, Session session, String state) {
        try(Jedis jedis = getJedisWithDb()){
            jedis.del(originalRequestKeyNameInSession(state));
        }
    }

    private Jedis getJedisWithDb(){
        Jedis jedis = pool.getResource();
        jedis.select(dbNumber);
        return jedis;
    }

    @Override
    public void postProcess(Exchange exchange) {
        //Nothing to do
    }

    public String getHost() {
        return host;
    }


    /**
     * @description Host address/url for Redis. Default is localhost
     */
    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * @description Port to connect to. Default value is 6379
     */
    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }


    /**
     * @description Limit for max body size. Exchanges will be truncated to max body size if they are bigger. Used unit is bytes.
     * Default value is 100000.
     */
    @MCAttribute
    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public int getDbNumber() {
        return dbNumber;
    }

    /**
     * @description Index number to connect to in Redis. Default is 0
     */
    @MCAttribute
    public void setDbNumber(int dbNumber) {
        this.dbNumber = dbNumber;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * @description This prefix is added to keys when storing data in Redis.
     */
    @MCAttribute
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * @description Timeout value when connecting to Redis instance. Default is 10000 milliseconds. Unit is in milliseconds
     */
    @MCAttribute
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getUser() {
        return user;
    }

    /**
     * @description Username to use when connecting redis.
     * */
    @MCAttribute
    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    /**
     * @description Password to use when connecting redis.
     * */
    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSsl() {
        return ssl;
    }

    /**
     * @description Used for enabling ssl. Default value is false
     * */
    @MCAttribute
    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }
}
