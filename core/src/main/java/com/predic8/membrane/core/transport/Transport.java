/* Copyright 2009, 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.springframework.beans.factory.*;

import java.io.*;
import java.util.*;

public abstract class Transport {

    /**
     * SSL and Non-SSL are mixed here, maybe split that in future
     */
    protected final Set<IPortChangeListener> menuListeners = new HashSet<>();

    private List<Interceptor> interceptors = new Vector<>();
    private Router router;
    private boolean reverseDNS = true;

    private int concurrentConnectionLimitPerIp = -1;

    public String getOpenBackendConnections(int port) {
        return "N/A";
    }

    public List<Interceptor> getFlow() {
        return interceptors;
    }

    @MCChildElement(allowForeign = true)
    public void setFlow(List<Interceptor> flow) {
        this.interceptors = flow;
    }

    public void init(Router router) throws Exception {
        this.router = router;

        if (interceptors.isEmpty()) {
            interceptors.add(getInterceptor(RuleMatchingInterceptor.class));
            interceptors.add(getInterceptor(LoggingContextInterceptor.class));
            interceptors.add(getExchangeStoreInterceptor());
            interceptors.add(getInterceptor(DispatchingInterceptor.class));
            interceptors.add(getInterceptor(ReverseProxyingInterceptor.class));
            interceptors.add(router.getGlobalInterceptor());
            interceptors.add(getInterceptor(UserFeatureInterceptor.class));
            interceptors.add(getInterceptor(InternalRoutingInterceptor.class));
            interceptors.add(getInterceptor(HTTPClientInterceptor.class));
        }

        for (Interceptor interceptor : interceptors) {
            interceptor.init(router);
        }
    }

    /**
     * Look up an interceptor in the Spring context; fall back to default construction.
     */
    private @NotNull <T extends Interceptor> T getInterceptor(Class<T> clazz) throws ReflectiveOperationException {
        BeanFactory bf = router.getBeanFactory();
        if (bf instanceof ListableBeanFactory lbf) {
            T bean = lbf.getBeanProvider(clazz).getIfAvailable();
            if (bean != null)
                return bean;
        }
        return clazz.getConstructor().newInstance();
    }

    /**
     * Look up an ExchangeStoreInterceptor in the Spring context; fall back to router-backed instance.
     */
    private @NotNull ExchangeStoreInterceptor getExchangeStoreInterceptor() {
        BeanFactory bf = router.getBeanFactory();
        if (bf instanceof ListableBeanFactory lbf) {
            ExchangeStoreInterceptor bean = lbf.getBeanProvider(ExchangeStoreInterceptor.class).getIfAvailable();
            if (bean != null)
                return bean;
        }
        return new ExchangeStoreInterceptor(router.getExchangeStore());
    }

    public Router getRouter() {
        return router;
    }

    public <T extends Interceptor> Optional<T> getFirstInterceptorOfType(Class<T> type) {
        return InterceptorUtil.getFirstInterceptorOfType(interceptors, type);
    }

    public void closeAll() {
        closeAll(true);
    }

    public void closeAll(boolean waitForCompletion) {
    }

    public void openPort(String ip, int port, SSLProvider sslProvider, TimerManager timerManager) throws IOException {
    }

    public void openPort(SSLableProxy proxy, TimerManager timerManager) throws IOException {
    }

    public abstract boolean isOpeningPorts();

    public boolean isReverseDNS() {
        return reverseDNS;
    }

    /**
     * @description Whether the remote address should automatically reverse-looked up for incoming connections.
     * @default true
     */
    @MCAttribute
    public void setReverseDNS(boolean reverseDNS) {
        this.reverseDNS = reverseDNS;
    }

    public int getConcurrentConnectionLimitPerIp() {
        return concurrentConnectionLimitPerIp;
    }

    /**
     * @description Limits the number of concurrent connections from one ip
     * @default -1 No Limit
     */
    @MCAttribute
    public void setConcurrentConnectionLimitPerIp(int concurrentConnectionLimitPerIp) {
        this.concurrentConnectionLimitPerIp = concurrentConnectionLimitPerIp;
    }
}
