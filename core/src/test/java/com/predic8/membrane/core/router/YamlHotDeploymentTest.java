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

import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.hotdeploy.YamlRouterReloader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlHotDeploymentTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldKeepPreviousRuntimeWhenValidationFails() throws Exception {
        Path config = tempDir.resolve("apis.yaml");
        Files.writeString(config, configWithInternal("first", "https://example.com"));

        DefaultRouter router = new DefaultRouter();
        try {
            YamlRouterReloader reloader = new YamlRouterReloader(router, loadIntoRouter(router, config.toString()));
            router.setConfigurationReloader(reloader);
            router.start();

            Files.writeString(config, """
                    configuration:
                      hotDeploy: false
                    ---
                    internal:
                      name: broken
                      target:
                        url: "https://example.org
                    """);

            assertTrue(reloader.reload());
            assertTrue(router.isRunning());
            assertEquals(List.of("first"), getRuleNames(router));
        } finally {
            router.stop();
        }
    }

    @Test
    void shouldReloadSuccessfully() throws Exception {
        Path config = tempDir.resolve("apis.yaml");
        Files.writeString(config, configWithInternal("first", "https://example.com"));

        DefaultRouter router = new DefaultRouter();
        try {
            YamlRouterReloader reloader = new YamlRouterReloader(router, loadIntoRouter(router, config.toString()));
            router.setConfigurationReloader(reloader);
            router.start();

            Files.writeString(config, configWithInternal("second", "https://example.org"));

            assertTrue(reloader.reload());
            assertTrue(router.isRunning());
            assertEquals(List.of("second"), getRuleNames(router));
        } finally {
            router.stop();
        }
    }

    @Test
    void shouldRestorePreviousRuntimeWhenReloadFailsAfterShutdown() throws Exception {
        Path config = tempDir.resolve("apis.yaml");
        Files.writeString(config, configWithInternal("first", "https://example.com"));

        FailingReloadStartRouter router = new FailingReloadStartRouter();
        try {
            YamlRouterReloader reloader = new YamlRouterReloader(router, loadIntoRouter(router, config.toString()));
            router.setConfigurationReloader(reloader);
            router.start();

            Files.writeString(config, configWithInternal("second", "https://example.org"));
            router.failNextStart();

            assertTrue(reloader.reload());
            assertTrue(router.isRunning());
            assertEquals(List.of("first"), getRuleNames(router));
        } finally {
            router.stop();
        }
    }

    @Test
    void shouldReturnFalseWhenReloadAndRollbackFailAfterShutdown() throws Exception {
        Path config = tempDir.resolve("apis.yaml");
        Files.writeString(config, configWithInternal("first", "https://example.com"));

        FailingReloadStartRouter router = new FailingReloadStartRouter();
        try {
            YamlRouterReloader reloader = new YamlRouterReloader(router, loadIntoRouter(router, config.toString()));
            router.setConfigurationReloader(reloader);
            router.start();

            Files.writeString(config, configWithInternal("second", "https://example.org"));
            router.failNextStarts(2);

            assertFalse(reloader.reload());
            assertFalse(router.isRunning());
        } finally {
            router.stop();
        }
    }

    private static List<String> getRuleNames(DefaultRouter router) {
        return router.getRuleManager().getRules().stream()
                .map(Proxy::getName)
                .toList();
    }

    private static String configWithInternal(String name, String url) {
        return """
                configuration:
                  hotDeploy: false
                ---
                internal:
                  name: %s
                  target:
                    url: %s
                """.formatted(name, url);
    }

    private static class FailingReloadStartRouter extends DefaultRouter {
        private int failingStarts;

        void failNextStart() {
            failNextStarts(1);
        }

        void failNextStarts(int failingStarts) {
            this.failingStarts = failingStarts;
        }

        @Override
        public void start() {
            if (failingStarts > 0) {
                failingStarts--;
                throw new RuntimeException("simulated reload start failure");
            }
            super.start();
        }
    }
}
