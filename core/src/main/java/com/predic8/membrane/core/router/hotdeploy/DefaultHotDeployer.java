package com.predic8.membrane.core.router.hotdeploy;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.spring.*;
import org.slf4j.*;
import org.springframework.context.*;
import org.springframework.context.support.*;

import java.util.*;

public class DefaultHotDeployer implements HotDeployer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHotDeployer.class.getName());

    /**
     * In case more than one <router hotDeploy="true" /> starts within the same
     * app context, we track them here, so they start only one
     * HotDeploymentThread.
     */
    protected static final HashSet<ApplicationContext> hotDeployingContexts = new HashSet<>();

    private HotDeploymentThread hdt;
    private final Router router;

    public DefaultHotDeployer(Router router) {
        this.router = router;
    }

    @Override
    public void start() {
        if (hdt != null)
            throw new IllegalStateException("Hot deployment already started.");

        if (!(router.getBeanFactory() instanceof TrackingApplicationContext tac)) {
            log.warn("""
                    ApplicationContext is not a TrackingApplicationContext. Please set <router hotDeploy="false">.
                    """);
            return;
        }

        synchronized (router.getLock()) {
            if (!(router.getBeanFactory() instanceof AbstractRefreshableApplicationContext bf))
                throw new RuntimeException("ApplicationContext is not a AbstractRefreshableApplicationContext. Please set <router hotDeploy=\"false\">.");
            synchronized (hotDeployingContexts) {
                if (hotDeployingContexts.contains(bf))
                    return;
                hotDeployingContexts.add(bf);
            }
            hdt = new HotDeploymentThread(bf);
            hdt.setFiles(tac.getFiles());
            hdt.start();
        }
    }

    @Override
    public void stop() {
        if (hdt == null)
            return;

        synchronized (hotDeployingContexts) {
            router.stopAutoReinitializer();
            hdt.stopASAP();
            hdt = null;

            hotDeployingContexts.remove(router.getBeanFactory());
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        synchronized (hotDeployingContexts) {
            if (enabled)
                start();
            else
                stop();
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
