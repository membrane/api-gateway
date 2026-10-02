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

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
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

    private DefaultRouter load(String yaml) throws Exception {
        Path config = tempDir.resolve("apis.yaml");
        Files.writeString(config, yaml);
        DefaultRouter router = new DefaultRouter();
        loadIntoRouter(router, config.toString());
        return router;
    }
}
