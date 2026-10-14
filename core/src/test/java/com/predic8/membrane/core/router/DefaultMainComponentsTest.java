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

import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;

class DefaultMainComponentsTest {

    @TempDir
    Path tempDir;

    @Test
    void transportFromComponents() throws Exception {
        DefaultRouter router = load("""
                components:
                  inbound:
                    transport:
                      backlog: 1234
                      socketTimeout: 5000
                ---
                internal:
                  name: dummy
                  target:
                    url: https://example.com
                """);
        try {
            router.init();
            HttpTransport transport = assertInstanceOf(HttpTransport.class, router.getTransport());
            assertEquals(1234, transport.getBacklog());
            assertEquals(5000, transport.getSocketTimeout());
            assertSame(router, transport.getRouter());
        } finally {
            router.stop();
        }
    }

    @Test
    void defaultTransportWithoutComponent() throws Exception {
        DefaultRouter router = load("""
                internal:
                  name: dummy
                  target:
                    url: https://example.com
                """);
        try {
            router.init();
            assertEquals(50, assertInstanceOf(HttpTransport.class, router.getTransport()).getBacklog());
        } finally {
            router.stop();
        }
    }

    /**
     * #3483: the components of a router must only use the router's own timer, so stopping the router leaves no
     * "Connection Closer" timer thread behind.
     */
    @Test
    void stoppedRouterLeavesNoTimerThreads() throws Exception {
        final var before = liveTimerThreads();

        new DefaultRouter().stop();

        final var started = liveTimerThreads();
        started.removeAll(before);
        for (final var thread : started)
            thread.join(5_000); // Timer.cancel() ends the thread asynchronously
        started.removeIf(thread -> !thread.isAlive());
        assertEquals(Set.of(), started);
    }

    /** java.util.Timer threads are named "Timer-N". */
    private static Set<Thread> liveTimerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith("Timer-"))
                .collect(toSet());
    }

    private DefaultRouter load(String yaml) throws Exception {
        Path config = tempDir.resolve("apis.yaml");
        Files.writeString(config, yaml);
        DefaultRouter router = new DefaultRouter();
        loadIntoRouter(router, config.toString());
        return router;
    }
}
