package com.predic8.membrane.core.router.hotdeploy;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.spring.*;
import org.slf4j.*;

public class DefaultHotDeployer implements HotDeployer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHotDeployer.class.getName());

    private HotDeploymentThread hdt;
    private final Router router;

    public DefaultHotDeployer(Router router) {
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
        if (hdt == null)
            return;

        router.stopAutoReinitializer();
        hdt.stopASAP();
        hdt = null;

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
