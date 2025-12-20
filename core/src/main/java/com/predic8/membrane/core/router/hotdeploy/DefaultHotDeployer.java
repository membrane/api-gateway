package com.predic8.membrane.core.router.hotdeploy;

import com.predic8.membrane.core.config.spring.*;
import com.predic8.membrane.core.router.*;
import org.slf4j.*;

import javax.annotation.concurrent.*;

public class DefaultHotDeployer implements HotDeployer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHotDeployer.class.getName());

    @GuardedBy("this")
    private HotDeploymentThread hdt;

    private Router router;

    @Override
    public void init(Router router) {
        this.router = router;
    }

    @Override
    public void start() {
        // Prevent multiple threads from starting hot deployment at the same time.
        synchronized (this) {
            if (hdt != null)
                throw new IllegalStateException("Hot deployment already started.");

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
        synchronized (this) {
            if (hdt == null)
                return;

            router.stopAutoReinitializer();
            hdt.stopASAP();
            hdt = null;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled)
            start();
        else
            stop();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
