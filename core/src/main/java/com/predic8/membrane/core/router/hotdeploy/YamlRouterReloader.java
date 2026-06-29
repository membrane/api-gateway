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

import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.YamlConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.nio.file.Path;
import java.util.List;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadSnapshotIntoRouter;

/**
 * Performs YAML validate-and-reload orchestration around the router lifecycle.
 */
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
        boolean passedShutdownPoint = false;

        if (!router.markReloading()) {
            return false;
        }

        YamlConfigurationSource sourceConfig = null;
        try {
            sourceConfig = getRequiredSource();

            log.debug("Reloading YAML configuration from {}.", sourceConfig.location());
            // Validation only bootstraps a fresh router; it does not call start() or bind ports.
            YamlConfigurationSource validatedSource = validate(sourceConfig.location());

            passedShutdownPoint = true;
            router.stopRuntimeForReload();
            router.resetRuntime();

            YamlConfigurationSource reloadedSource = loadSnapshotIntoRouter(router, validatedSource);
            router.start();
            setSource(reloadedSource);
            log.info("Configuration Reloaded.");
            return true;
        } catch (Exception e) {
            logReloadFailure(e);
            if (!passedShutdownPoint) {
                return keepLastKnownGoodRuntimeAfterValidationFailure();
            }
            return restoreLastKnownGoodRuntime(sourceConfig, e);
        } finally {
            router.clearReloading();
        }
    }

    private void logReloadFailure(Exception e) {
        if (e instanceof ConfigurationParsingException) {
            log.error("Could not reload YAML configuration: {}", e.getMessage());
            return;
        }
        log.error("Could not reload YAML configuration.", e);
    }

    private boolean keepLastKnownGoodRuntimeAfterValidationFailure() {
        if (router.isRunning()) {
            log.info("Keeping the previous YAML runtime because reload validation failed before shutdown.");
            return true;
        }
        log.error("Reload validation failed before shutdown, but no previous YAML runtime is running.");
        return false;
    }

    private boolean restoreLastKnownGoodRuntime(YamlConfigurationSource yamlSource, Exception reloadFailure) {
        try {
            cleanupFailedRuntime(reloadFailure);
            router.resetRuntime();
            YamlConfigurationSource restoredSource = loadSnapshotIntoRouter(router, yamlSource);
            router.start();
            setSource(restoredSource);
            log.warn("Reload failed after shutdown; restored previous YAML runtime.");
            return true;
        } catch (Exception rollbackError) {
            rollbackError.addSuppressed(reloadFailure);
            cleanupFailedRuntime(rollbackError);
            log.error("Reload failed and previous YAML runtime could not be restored.", rollbackError);
            return false;
        }
    }

    private void cleanupFailedRuntime(Exception failure) {
        try {
            router.stopRuntimeForReload();
        } catch (Exception cleanupError) {
            failure.addSuppressed(cleanupError);
            log.error("Could not clean up failed YAML runtime.", cleanupError);
        }
    }

    private YamlConfigurationSource validate(String location) throws Exception {
        DefaultRouter candidate = new DefaultRouter();
        Exception validationFailure = null;
        try {
            return loadIntoRouter(candidate, location);
        } catch (Exception e) {
            validationFailure = e;
            throw e;
        } finally {
            cleanupValidationCandidate(candidate, validationFailure);
        }
    }

    private void cleanupValidationCandidate(DefaultRouter candidate, Exception validationFailure) {
        try {
            candidate.shutdownRuntimeComponents();
        } catch (Exception cleanupError) {
            if (validationFailure != null) {
                validationFailure.addSuppressed(cleanupError);
            }
            log.error("Could not clean up YAML validation runtime.", cleanupError);
        }
    }

    private synchronized YamlConfigurationSource getSource() {
        return source;
    }

    private YamlConfigurationSource getRequiredSource() {
        YamlConfigurationSource source = getSource();
        if (source == null || source.location() == null || source.location().isBlank()) {
            throw new IllegalStateException("No YAML configuration location is known.");
        }
        return source;
    }

    private synchronized void setSource(YamlConfigurationSource source) {
        this.source = source;
    }
}
