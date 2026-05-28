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

package com.predic8.membrane.core.router.hotdeploy;

import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.YamlConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.nio.file.Path;
import java.util.List;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;

public final class YamlRouterReloader implements ConfigurationReloader {

    private static final Logger log = LoggerFactory.getLogger(YamlRouterReloader.class);

    private final DefaultRouter router;

    @GuardedBy("this")
    private YamlConfigurationSource source;

    public YamlRouterReloader(DefaultRouter router, YamlConfigurationSource source) {
        this.router = router;
        this.source = source;
    }

    @Override
    public synchronized List<Path> trackedFiles() {
        return source == null ? List.of() : source.trackedFiles();
    }

    @Override
    public boolean reload() {
        YamlConfigurationSource currentSource = getSource();
        boolean runtimeStopped = false;

        if (!router.markReloading()) {
            return false;
        }

        try {
            if (currentSource == null || currentSource.location() == null || currentSource.location().isBlank()) {
                throw new IllegalStateException("No YAML configuration location is known.");
            }

            log.info("Reloading YAML configuration from {}.", currentSource.location());
            validate(currentSource.location());

            router.stopRuntimeForReload();
            runtimeStopped = true;
            router.resetRuntime();

            setSource(loadIntoRouter(router, currentSource.location()));
            router.start();
            log.info("YAML configuration reloaded successfully.");
            return true;
        } catch (Exception e) {
            log.error("Could not reload YAML configuration.", e);
            if (!runtimeStopped) {
                log.info("Keeping the previous YAML runtime because reload validation failed before shutdown.");
                return true;
            }
            try {
                router.stopRuntimeForReload();
                router.disposeRuntime();
            } catch (Exception cleanupError) {
                cleanupError.addSuppressed(e);
                log.error("Could not clean up the failed YAML runtime after reload failure.", cleanupError);
            }
            log.info("YAML runtime remains stopped after reload failure.");
            return false;
        } finally {
            router.clearReloading();
        }
    }

    private void validate(String location) throws Exception {
        DefaultRouter candidate = new DefaultRouter();
        try {
            loadIntoRouter(candidate, location);
        } finally {
            candidate.disposeRuntime();
        }
    }

    private synchronized YamlConfigurationSource getSource() {
        return source;
    }

    private synchronized void setSource(YamlConfigurationSource source) {
        this.source = source;
    }
}
