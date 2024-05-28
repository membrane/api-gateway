package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static com.predic8.membrane.core.util.NetworkUtil.getFreePortEqualAbove;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkUtilTest {
    @Test
    void testGetRandomPortEqualAbove3000() throws Exception {
        assertTrue(getFreePortEqualAbove(3000) >= 3000);
    }

    @Test
    void testFailToGetPortAbove65534() {
        try (ServerSocket ignored = new ServerSocket(65535)) {
            assertThrows(IOException.class, () -> getFreePortEqualAbove(65535));
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind port 65535.", e);
        }
    }
}