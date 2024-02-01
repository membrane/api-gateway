package com.predic8.membrane.core.util;

import net.rubyeye.xmemcached.exception.MemcachedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class MemcachedConnectorTest {

    public MemcachedConnector connector;

    @BeforeEach
    void setUp() throws Exception {
        connector = new MemcachedConnector();
        connector.afterPropertiesSet();
    }

    @Test
    void doesSomething() {
        try {
            assertNull(connector.getClient().get("gibts_nicht"));
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            throw new RuntimeException(e);
        }
    }
}