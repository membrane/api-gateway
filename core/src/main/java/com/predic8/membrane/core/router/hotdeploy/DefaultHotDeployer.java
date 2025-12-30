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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.spring.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.annotation.concurrent.*;

public class DefaultHotDeployer implements HotDeployer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHotDeployer.class.getName());

    @GuardedBy("lock")
    private HotDeploymentThread hdt;

    private Router router;

    private final Object lock = new Object();

    @Override
    public void start(@NotNull Router router) {
        this.router = router;
        startInternal();
    }

    private void startInternal() {
        // Prevent multiple threads from starting hot deployment at the same time.
        synchronized (lock) {
            if (hdt != null) {
                log.warn("Hot deployment already started.");
                return;
            }

            if (!(router.getBeanFactory() instanceof TrackingApplicationContext tac)) {
                log.warn("""
                        ApplicationContext is not a TrackingApplicationContext. Please set <router hotDeploy="false">.
                        """);
                return;
            }

            hdt = new HotDeploymentThread(router.getRef());
            hdt.setFiles(tac.getFiles());
            hdt.start();
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (hdt == null)
                return;

            router.getReinitializer().stop();
            hdt.stopASAP();
            hdt = null;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled && router != null)
            startInternal();
        else
            stop();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
