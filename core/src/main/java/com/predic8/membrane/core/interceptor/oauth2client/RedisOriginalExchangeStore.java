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
import com.predic8.membrane.core.util.RedisConnector;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;


/**
 * @description  Used for storing exchanges temporarily in Redis. Supports authentication with or without password and username
 */
@MCElement(name = "redisOriginalExchangeStore")
public class RedisOriginalExchangeStore extends OriginalExchangeStore {
    private int maxBodySize = 100000;
    private String prefix;
    private ObjectMapper objMapper;
    private RedisConnector connector;


    public RedisOriginalExchangeStore(){
        objMapper = new ObjectMapper();
    }



    private String originalRequestKeyNameInSession(String state) {
        return prefix != null ? prefix + state : state;
    }

    @Override
    public void store(Exchange exchange, Session session, String state, Exchange exchangeToStore) throws IOException {
        connector.getJedisWithDb().setex(originalRequestKeyNameInSession(state), 3600, objMapper.writeValueAsString(getTrimmedAbstractExchangeSnapshot(exchangeToStore, maxBodySize)) );
    }

    @Override
    public AbstractExchangeSnapshot reconstruct(Exchange exchange, Session session, String state) {
        try {
            return objMapper.readValue(connector.getJedisWithDb().get(originalRequestKeyNameInSession(state)), AbstractExchangeSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void remove(Exchange exchange, Session session, String state) {
        connector.getJedisWithDb().del(originalRequestKeyNameInSession(state));
    }


    @Override
    public void postProcess(Exchange exchange) {
        //Nothing to do
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


    public RedisConnector getConnector() {
        return connector;
    }

    @MCAttribute
    public void setConnector(RedisConnector connector) {
        this.connector = connector;
    }
}
