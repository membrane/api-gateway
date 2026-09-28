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

import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadPoolExecutor;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static org.junit.jupiter.api.Assertions.*;

class YamlRouterBootstrapTest {

    @TempDir
    Path tempDir;

    @Test
    void configurationTransportIsAppliedToTheTransport() throws Exception {
        DefaultRouter router = new DefaultRouter();
        try {
            loadIntoRouter(router, write("""
                    configuration:
                      hotDeploy: false
                      transport:
                        backlog: 1024
                        socketTimeout: 12000
                        tcpNoDelay: false
                        coreThreadPoolSize: 5
                        maxThreadPoolSize: 300
                        reverseDNS: false
                        concurrentConnectionLimitPerIp: 100
                    """));
            router.init();

            HttpTransport transport = (HttpTransport) router.getTransport();
            assertEquals(1024, transport.getBacklog());
            assertEquals(12000, transport.getSocketTimeout());
            assertFalse(transport.isTcpNoDelay());
            assertEquals(5, ((ThreadPoolExecutor) transport.getExecutorService()).getCorePoolSize());
            assertEquals(300, ((ThreadPoolExecutor) transport.getExecutorService()).getMaximumPoolSize());
            assertFalse(transport.isReverseDNS());
            assertEquals(100, transport.getConcurrentConnectionLimitPerIp());
        } finally {
            router.stop();
        }
    }

    @Test
    void configurationTransportWithCoreThreadPoolSizeAboveMaxIsRejectedWhileParsing() throws Exception {
        String config = write("""
                configuration:
                  transport:
                    maxThreadPoolSize: 10
                """);

        var e = assertThrows(ConfigurationParsingException.class, () -> loadIntoRouter(new DefaultRouter(), config));
        assertTrue(e.getMessage().contains("maxThreadPoolSize (10)"), e.getMessage());
    }

    @Test
    void transportComponentIsRejected() throws Exception {
        String config = write("""
                components:
                  myTransport:
                    transport:
                      backlog: 1024
                """);

        var e = assertThrows(ConfigurationParsingException.class, () -> loadIntoRouter(new DefaultRouter(), config));
        assertTrue(e.getMessage().contains("#/components/myTransport"), e.getMessage());
        assertTrue(e.getMessage().contains("under 'configuration'"), e.getMessage());
    }

    private String write(String yaml) throws Exception {
        Path config = tempDir.resolve("apis.yaml");
        Files.writeString(config, yaml);
        return config.toString();
    }
}
