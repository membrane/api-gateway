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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.ReverseProxyingInterceptor;
import com.predic8.membrane.core.proxies.SSLableProxy;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.http.method.DefaultMethodValidator;
import com.predic8.membrane.core.transport.http.method.MethodValidator;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

public abstract class Transport {

    /**
     * SSL and Non-SSL are mixed here, maybe split that in future
     */
    private List<Interceptor> interceptors = new Vector<>();

    private Router router;
    private boolean reverseDNS = true;

    private MethodValidator methodValidator = new DefaultMethodValidator();

    private int concurrentConnectionLimitPerIp = -1;

    public String getOpenBackendConnections(int port) {
        return "N/A";
    }

    public List<Interceptor> getFlow() {
        return interceptors;
    }

    /**
     * @description Flow every exchange runs through. Without it, Membrane uses a built-in flow that matches the
     * request to an API, runs the API's flow and forwards the request to the target. A configured flow replaces that
     * built-in flow completely, so it has to contain those steps itself.
     * <p><b>Attention:</b> Changing this flow changes Membrane's internal architecture. Normally it never needs to be
     * changed; only configure it if you know exactly what you are doing.</p>
     */
    @MCChildElement(allowForeign = true)
    public void setFlow(List<Interceptor> flow) {
        this.interceptors = flow;
    }

    public void init(Router router) {
        this.router = router;

        if (router != null && router.getRegistry() != null)
            router.getRegistry().getBean(MethodValidator.class).ifPresent(v -> methodValidator = v);

        if (interceptors.isEmpty()) {
            interceptors.add(getInterceptor(RuleMatchingInterceptor.class));
            interceptors.add(getInterceptor(LoggingContextInterceptor.class));
            interceptors.add(getExchangeStoreInterceptor());
            interceptors.add(getInterceptor(DispatchingInterceptor.class));
            interceptors.add(getInterceptor(ReverseProxyingInterceptor.class));
            if (router instanceof DefaultRouter dr)
                dr.getRegistry().getBean(GlobalInterceptor.class).ifPresent(i -> interceptors.add(i ));
            interceptors.add(getInterceptor(UserFeatureInterceptor.class));
            interceptors.add(getInterceptor(InternalRoutingInterceptor.class));

            if(router instanceof DefaultRouter r)
                interceptors.add(new HTTPClientInterceptor(r.getHttpClient()));
            else interceptors.add(getInterceptor(HTTPClientInterceptor.class));
        }

        for (Interceptor interceptor : interceptors) {
            interceptor.init(router);
        }
    }

    /**
     * Look up an interceptor in the Spring context; fall back to default construction.
     */
    private @NotNull <T extends Interceptor> T getInterceptor(Class<T> clazz)  {
        BeanFactory bf = router.getBeanFactory();
        if (bf instanceof ListableBeanFactory lbf) {
            T bean = lbf.getBeanProvider(clazz).getIfAvailable();
            if (bean != null)
                return bean;
        }
        try {
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate object of class %s".formatted(clazz),e);
        }
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

    /**
     * The policy deciding which request methods are accepted. Uses a {@code <methodValidator>} component if one is
     * declared.
     */
    public MethodValidator getMethodValidator() {
        return methodValidator;
    }

    public <T extends Interceptor> Optional<T> getFirstInterceptorOfType(Class<T> type) {
        return InterceptorUtil.getFirstInterceptorOfType(interceptors, type);
    }

    public void closeAll() {
        closeAll(true);
    }

    public void closeAll(boolean waitForCompletion) {
    }

    public void openPort(String ip, int port, SSLProvider sslProvider) throws IOException {
    }

    public void openPort(SSLableProxy proxy) throws IOException {
    }

    public abstract boolean isOpeningPorts();

    public boolean isReverseDNS() {
        return reverseDNS;
    }

    /**
     * @description Whether to resolve the client IP address to a host name via reverse DNS lookup. The result is cached
     * and becomes the remote address of the exchange, e.g. in logs. When <code>false</code>, the IP address is used
     * and no lookup is made.
     * @default true
     * @example false
     */
    @MCAttribute
    public void setReverseDNS(boolean reverseDNS) {
        this.reverseDNS = reverseDNS;
    }

    public int getConcurrentConnectionLimitPerIp() {
        return concurrentConnectionLimitPerIp;
    }

    /**
     * @description Maximum number of concurrent connections from a single client IP address. A connection beyond the
     * limit is closed after a <code>429</code> Problem Details response, or a TLS alert on TLS ports.
     * <code>-1</code> disables the limit.
     * @default -1
     * @example 100
     */
    @MCAttribute
    public void setConcurrentConnectionLimitPerIp(int concurrentConnectionLimitPerIp) {
        this.concurrentConnectionLimitPerIp = concurrentConnectionLimitPerIp;
    }
}
