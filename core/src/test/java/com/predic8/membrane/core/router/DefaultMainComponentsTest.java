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

package com.predic8.membrane.core.router;

import com.predic8.membrane.core.transport.TransportConfiguration;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMainComponentsTest {

    @Test
    void explicitTransportWithoutOwnSettingsUsesTransportConfig() {
        DefaultRouter router = new DefaultRouter();
        try {
            HttpTransport transport = new HttpTransport();
            router.setTransport(transport);
            router.getConfiguration().setTransportConfig(transportConfigWithBacklog(1024));

            router.init();

            assertEquals(1024, transport.getBacklog());
        } finally {
            router.stop();
        }
    }

    @Test
    void transportSettingsInBothPlacesAreRejected() {
        DefaultRouter router = new DefaultRouter();
        try {
            HttpTransport transport = new HttpTransport();
            transport.setBacklog(10);
            router.setTransport(transport);
            router.getConfiguration().setTransportConfig(transportConfigWithBacklog(1024));

            var e = assertThrows(ConfigurationException.class, router::init);
            assertTrue(e.getMessage().contains("<configuration><transport>"), e.getMessage());
        } finally {
            router.stop();
        }
    }

    @Test
    void explicitTransportSettingsAreKeptWithoutTransportConfig() {
        DefaultRouter router = new DefaultRouter();
        try {
            HttpTransport transport = new HttpTransport();
            transport.setBacklog(10);
            router.setTransport(transport);

            router.init();

            assertEquals(10, transport.getBacklog());
        } finally {
            router.stop();
        }
    }

    private static TransportConfiguration transportConfigWithBacklog(int backlog) {
        var config = new TransportConfiguration();
        config.setBacklog(backlog);
        return config;
    }
}
