package com.predic8.membrane.core.interceptor.oauth2.client;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ServiceProxy;

import java.io.IOException;

public abstract class AuthServerMock extends HttpRouter {
    public AuthServerMock() throws IOException {
        getTransport().setBacklog(10_000);
        getTransport().setSocketTimeout(10_000);
        setHotDeploy(false);
        getTransport().setConcurrentConnectionLimitPerIp(500);

        getRuleManager().addProxyAndOpenPortIfNew(mockServiceProxy());
        start();
    }

    protected abstract ServiceProxy mockServiceProxy();
}
