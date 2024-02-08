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
package com.predic8.membrane.core.interceptor.oauth2client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.util.MemcachedConnector;
import net.rubyeye.xmemcached.exception.MemcachedException;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

@MCElement(name = "memcachedOriginalExchangeStore")
public class MemcachedOriginalExchangeStore extends OriginalExchangeStore {

    private int maxBodySize = 100000;
    private MemcachedConnector connector;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void store(Exchange exchange, Session session, String state, Exchange exchangeToStore) throws IOException {
        try {
            connector.getClient().set(
                    state,
                    3600,
                    objectMapper.writeValueAsString(getTrimmedAbstractExchangeSnapshot(exchangeToStore, maxBodySize))
            );
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AbstractExchangeSnapshot reconstruct(Exchange exchange, Session session, String state) {
        try {
            String key = connector.getClient().get(state);
            return objectMapper.readValue(key, AbstractExchangeSnapshot.class);
        } catch (TimeoutException | InterruptedException | MemcachedException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove(Exchange exc, Session session, String state) {
        try {
            connector.getClient().delete(state);
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void postProcess(Exchange exc) {

    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    @MCAttribute
    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public MemcachedConnector getConnector() {
        return connector;
    }

    @MCAttribute
    public void setConnector(MemcachedConnector connector) {
        this.connector = connector;
    }
}
