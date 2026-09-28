/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport;

import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransportConfigurationTest {

    @Test
    void defaults() {
        var config = new TransportConfiguration();
        assertEquals(500, config.getBacklog());
        assertEquals(30000, config.getSocketTimeout());
        assertTrue(config.isTcpNoDelay());
        assertEquals(30000, config.getForceSocketCloseOnHotDeployAfter());
        assertEquals(20, config.getCoreThreadPoolSize());
        assertEquals(Integer.MAX_VALUE, config.getMaxThreadPoolSize());
        assertTrue(config.isReverseDNS());
        assertEquals(-1, config.getConcurrentConnectionLimitPerIp());
        assertDoesNotThrow(config::validate);
    }

    @Test
    void coreThreadPoolSizeAboveMaxIsRejected() {
        var config = new TransportConfiguration();
        config.setMaxThreadPoolSize(10);

        var e = assertThrows(ConfigurationException.class, config::validate);
        assertTrue(e.getMessage().contains("coreThreadPoolSize (20)"), e.getMessage());
        assertTrue(e.getMessage().contains("maxThreadPoolSize (10)"), e.getMessage());
    }

    @Test
    void negativeCoreThreadPoolSizeIsRejected() {
        var config = new TransportConfiguration();
        config.setCoreThreadPoolSize(-1);

        var e = assertThrows(ConfigurationException.class, config::validate);
        assertTrue(e.getMessage().contains("coreThreadPoolSize"), e.getMessage());
    }

    @Test
    void maxThreadPoolSizeBelowOneIsRejected() {
        var config = new TransportConfiguration();
        config.setCoreThreadPoolSize(0);
        config.setMaxThreadPoolSize(0);

        var e = assertThrows(ConfigurationException.class, config::validate);
        assertTrue(e.getMessage().contains("maxThreadPoolSize"), e.getMessage());
    }
}
