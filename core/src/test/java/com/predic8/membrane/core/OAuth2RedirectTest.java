package com.predic8.membrane.core;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class OAuth2RedirectTest {

    static Router browserRouter;
    static Router membraneRouter;
    static Router azureRouter;
    static Router nginxRouter;

    @BeforeAll
    static void setup() throws Exception {
        Rule browserRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2000), null, 0);
        Rule membraneRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2001), null, 0);
        Rule azureRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2002), null, 0);
        Rule nginxRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2003), null, 0);

        browserRouter = new Router();
        browserRouter.getRuleManager().addProxyAndOpenPortIfNew(browserRule);
        browserRouter.init();

        membraneRouter = new Router();
        membraneRouter.getRuleManager().addProxyAndOpenPortIfNew(membraneRule);
        membraneRouter.init();

        azureRouter = new Router();
        azureRouter.getRuleManager().addProxyAndOpenPortIfNew(azureRule);
        azureRouter.init();

        nginxRouter = new Router();
        nginxRouter.getRuleManager().addProxyAndOpenPortIfNew(nginxRule);
        nginxRouter.init();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        browserRouter.shutdown();
        membraneRouter.shutdown();
        azureRouter.shutdown();
        nginxRouter.shutdown();
    }

}
