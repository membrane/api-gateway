package com.predic8.membrane.core.router.hotdeploy;

import com.predic8.membrane.core.router.*;

public interface HotDeployer {

    void start();

    void stop();

    void setEnabled(boolean enabled);

    default boolean isEnabled() {
        return false;
    }

    default void init(Router router) {}
}

