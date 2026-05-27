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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class YamlHotDeploymentThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(YamlHotDeploymentThread.class.getName());

    private final DefaultRouter router;
    private final DefaultHotDeployer hotDeployer;
    private final List<FileInfo> files = new ArrayList<>();

    private static class FileInfo {
        public String file;
        public long lastModified;
    }

    public YamlHotDeploymentThread(DefaultRouter router, DefaultHotDeployer hotDeployer) {
        super("yaml-hotdeploy");
        this.router = router;
        this.hotDeployer = hotDeployer;
        refreshTrackedFiles();
    }

    private void refreshTrackedFiles() {
        files.clear();
        LinkedHashSet<String> trackedPaths = new LinkedHashSet<>();
        for (File file : router.getYamlTrackedFiles()) {
            trackedPaths.add(file.getAbsolutePath());
            File parent = file.getParentFile();
            if (parent != null) {
                trackedPaths.add(parent.getAbsolutePath());
            }
        }
        for (String path : trackedPaths) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.file = path;
            files.add(fileInfo);
        }
        updateLastModified();
    }

    private void updateLastModified() {
        for (FileInfo fileInfo : files) {
            fileInfo.lastModified = getLastModified(fileInfo.file);
        }
    }

    private static long getLastModified(String file) {
        return new File(file).lastModified();
    }

    private boolean configurationChanged() {
        for (FileInfo fileInfo : files) {
            if (getLastModified(fileInfo.file) != fileInfo.lastModified) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void run() {
        log.debug("YAML Hot Deployment Thread started.");

        while (!isInterrupted() && hotDeployer.isEnabled()) {
            try {
                while (!configurationChanged()) {
                    if (isInterrupted() || !hotDeployer.isEnabled()) {
                        log.debug("YAML Hot Deployment Thread interrupted.");
                        return;
                    }

                    //noinspection BusyWait
                    sleep(1000);
                }

                log.debug("yaml configuration changed.");

                if (!router.reloadYamlConfiguration()) {
                    break;
                }

                if (!hotDeployer.isEnabled()) {
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
