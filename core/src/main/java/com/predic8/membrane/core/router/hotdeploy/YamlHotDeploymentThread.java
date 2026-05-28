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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;

import static com.predic8.membrane.core.router.hotdeploy.FileWatchSnapshot.capture;

public class YamlHotDeploymentThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(YamlHotDeploymentThread.class.getName());

    private final ConfigurationReloader reloader;
    private final BooleanSupplier enabled;
    private FileWatchSnapshot snapshot;

    public YamlHotDeploymentThread(ConfigurationReloader reloader, BooleanSupplier enabled) {
        super("yaml-hotdeploy");
        this.reloader = reloader;
        this.enabled = enabled;
        refreshTrackedFiles();
    }

    private void refreshTrackedFiles() {
        snapshot = capture(reloader.trackedFiles());
    }

    @Override
    public void run() {
        log.debug("YAML Hot Deployment Thread started.");

        while (!isInterrupted() && enabled.getAsBoolean()) {
            try {
                while (!snapshot.hasChanged()) {
                    if (isInterrupted() || !enabled.getAsBoolean()) {
                        log.debug("YAML Hot Deployment Thread interrupted.");
                        return;
                    }

                    //noinspection BusyWait
                    sleep(1000);
                }

                log.info("Configuration Changed.");

                if (!reloader.reload()) {
                    break;
                }

                if (!enabled.getAsBoolean()) {
                    break;
                }

                refreshTrackedFiles();
            } catch (InterruptedException e) {
                interrupt();
            } catch (Exception e) {
                log.error("Could not redeploy YAML configuration.", e);
                break;
            }
        }

        log.debug("YAML Hot Deployment Thread interrupted.");
    }

    public void stopASAP() {
        interrupt();
    }
}
