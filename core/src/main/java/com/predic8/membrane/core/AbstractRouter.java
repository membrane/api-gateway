package com.predic8.membrane.core;

import com.predic8.membrane.core.proxies.*;
import org.slf4j.*;

public abstract class AbstractRouter implements IRouter {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    protected void initProxies() {
        log.debug("Initializing proxies.");
        for (Proxy proxy : getRuleManager().getRules()) {
            log.debug("Initializing proxy {}.", proxy.getName());
            proxy.init(this);
        }
    }
}
