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
    private ObjectMapper objectMapper = new ObjectMapper();

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
