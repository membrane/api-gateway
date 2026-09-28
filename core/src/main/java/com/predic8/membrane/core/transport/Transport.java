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

    private static final TransportConfiguration DEFAULT_TRANSPORT_CONFIG = new TransportConfiguration();

    private Router router;

    private MethodValidator methodValidator = new DefaultMethodValidator();

    /**
     * null: not configured on this transport, the defaults apply. Set by {@link #setTransportConfig} or created by
     * the first setter call on this transport (e.g. an XML attribute).
     */
    private TransportConfiguration transportConfig;

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

    public void init(Router router) {
        this.router = router;
        getSettings().validate();

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
        return getSettings().isReverseDNS();
    }

    /**
     * @description Whether the remote address should automatically reverse-looked up for incoming connections.
     * @default true
     */
    @MCAttribute
    public void setReverseDNS(boolean reverseDNS) {
        getOwnSettings().setReverseDNS(reverseDNS);
    }

    public int getConcurrentConnectionLimitPerIp() {
        return getSettings().getConcurrentConnectionLimitPerIp();
    }

    /**
     * @description Limits the number of concurrent connections from one ip
     * @default -1 No Limit
     */
    @MCAttribute
    public void setConcurrentConnectionLimitPerIp(int concurrentConnectionLimitPerIp) {
        getOwnSettings().setConcurrentConnectionLimitPerIp(concurrentConnectionLimitPerIp);
    }

    /**
     * @return the settings configured on this transport, or null if none were configured and the defaults apply
     */
    public TransportConfiguration getTransportConfig() {
        return transportConfig;
    }

    public void setTransportConfig(TransportConfiguration transportConfig) {
        this.transportConfig = transportConfig;
    }

    /**
     * The settings in effect: the configured ones, or the defaults.
     */
    protected TransportConfiguration getSettings() {
        return transportConfig != null ? transportConfig : DEFAULT_TRANSPORT_CONFIG;
    }

    /**
     * The settings of this transport, created on first use, for the setters to write into.
     */
    protected TransportConfiguration getOwnSettings() {
        if (transportConfig == null)
            transportConfig = new TransportConfiguration();
        return transportConfig;
    }
}
