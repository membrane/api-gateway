package com.predic8.membrane.core.interceptor.session;

import com.predic8.membrane.core.util.MemcachedConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemcachedSessionManagerTest {

    private MemcachedSessionManager sessionManager;

    @BeforeEach
    void setUp() throws Exception {
        sessionManager = new MemcachedSessionManager();
        sessionManager.setConnector(new MemcachedConnector());
        sessionManager.init(null);
    }
}