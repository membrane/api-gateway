/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.router;

import com.predic8.membrane.annot.beanregistry.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http.streampump.*;
import com.predic8.membrane.core.util.*;
import org.springframework.context.*;

import java.io.*;

import static com.predic8.membrane.core.proxies.RuleManager.RuleDefinitionSource.*;


/**
 * Opens ports but does not start hotdeployer, reinit, ...
 * Allows to overwrite components with setters.
 * <p>
 * Limitations:
 * - Does not support Registry
 */
public class TestRouter extends AbstractRouter implements BeanRegistryAware {

    protected BeanRegistry registry = new BeanRegistryImplementation(null, this, null);

    protected Transport transport = new HttpTransport();

    private final TimerManager timerManager = new TimerManager();
    private final HttpClientFactory httpClientFactory = new HttpClientFactory(timerManager);
    private final HttpClientConfiguration httpClientConfiguration = new HttpClientConfiguration();
    private final KubernetesClientFactory kubernetesClientFactory = new KubernetesClientFactory(httpClientFactory);
    protected ResolverMap resolverMap;

    protected RuleManager ruleManager = new RuleManager();

    protected final Statistics statistics = new Statistics();

    protected final DNSCache dnsCache = new DNSCache();

    protected Configuration configuration = new Configuration();

    protected FlowController flowController = new FlowController(this);

    protected ExchangeStore exchangeStore = new ForgetfulExchangeStore();


    @Override
    public void init() {
        transport.init(this);
        ruleManager.setRouter(this);
    }

    @Override
    public void start() {
        init();
        initProxies();
        try {
            getRuleManager().openPorts();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public TestRouter(ProxyConfiguration proxyConfiguration) {
        this();
        if (proxyConfiguration != null)
            getResolverMap().getHTTPSchemaResolver().getHttpClientConfig().setProxy(proxyConfiguration);
    }

    public TestRouter() {
        resolverMap = new ResolverMap(httpClientFactory, kubernetesClientFactory);
        resolverMap.addRuleResolver(this);
    }

    @Override
    public HttpTransport getTransport() {
        return (HttpTransport) transport;

    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    @Override
    public FlowController getFlowController() {
        return flowController;
    }

    @Override
    public ExchangeStore getExchangeStore() {
        return exchangeStore;
    }

    public void setExchangeStore(ExchangeStore exchangeStore) {
        this.exchangeStore = exchangeStore;
    }

    @Override
    public RuleManager getRuleManager() {
        return ruleManager;
    }

    @Override
    public ResolverMap getResolverMap() {
        return resolverMap;
    }

    @Override
    public DNSCache getDnsCache() {
        return dnsCache;
    }

    @Override
    public ApplicationContext getBeanFactory() {
        return null;
    }

    @Override
    public KubernetesClientFactory getKubernetesClientFactory() {
        return kubernetesClientFactory;
    }

    @Override
    public HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    @Override
    public HttpClientConfiguration getHttpClientConfig() {
        return httpClientConfiguration;
    }

    @Override
    public TimerManager getTimerManager() {
        return timerManager;
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    public BeanRegistry getRegistry() {
        return registry;
    }

    @Override
    public void add(Proxy proxy) throws IOException {
        ruleManager.addProxy(proxy, MANUAL);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public String getBaseLocation() {
        return "";
    }

    @Override
    public void stop() {
        transport.closeAll();
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void setRegistry(BeanRegistry registry) {
        this.registry = registry;
    }
}
