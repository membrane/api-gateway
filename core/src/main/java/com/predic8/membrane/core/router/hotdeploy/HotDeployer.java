package com.predic8.membrane.core.router.hotdeploy;

public interface HotDeployer {

    void start();

    void stop();

    void setEnabled(boolean enabled);

    default boolean isEnabled() {
        return false;
    }
}

