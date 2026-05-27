/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.config.spring.*;
import com.predic8.membrane.core.router.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.annotation.concurrent.*;

public class DefaultHotDeployer implements HotDeployer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHotDeployer.class.getName());

    @GuardedBy("lock")
    private Thread hdt;

    private DefaultRouter router;

    private final Object lock = new Object();
    private boolean enabled = true;

    @Override
    public void start(@NotNull DefaultRouter defaultRouter) {
        this.router = defaultRouter;
        startInternal();
    }

    private void startInternal() {
        // Prevent multiple threads from starting hot deployment at the same time.
        synchronized (lock) {
            if (!enabled) {
                return;
            }

            if (hdt != null) {
                if (!hdt.isAlive()) {
                    hdt = null;
                } else {
                    return;
                }
            }

            if (router == null) {
                return;
            }

            // Start from XML
            if (router.getBeanFactory() != null) {
                if (!(router.getBeanFactory() instanceof TrackingApplicationContext tac)) {
                    log.warn("""
                            ApplicationContext is not a TrackingApplicationContext. Please set <router hotDeploy="false">.
                            """);
                    return;
                }
                HotDeploymentThread hotDeploymentThread = new HotDeploymentThread(router.getRef());
                hotDeploymentThread.setFiles(tac.getFiles());
                hdt = hotDeploymentThread;
                hotDeploymentThread.start();
                return;
            }

            if (router.getYamlConfigurationLocation() != null && !router.getYamlTrackedFiles().isEmpty()) {
                hdt = new YamlHotDeploymentThread(router, this);
                hdt.start();
                return;
            }

            log.debug("Hot deployment skipped because no local YAML files are known.");
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (hdt == null)
                return;

            if (router != null && router.getReinitializer() != null) {
                router.getReinitializer().stop();
            }

            if (hdt instanceof HotDeploymentThread hotDeploymentThread) {
                hotDeploymentThread.stopASAP();
            } else if (hdt instanceof YamlHotDeploymentThread yamlHotDeploymentThread) {
                yamlHotDeploymentThread.stopASAP();
            } else {
                hdt.interrupt();
            }
            hdt = null;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (enabled && router != null) {
            startInternal();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
