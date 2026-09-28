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

import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import com.predic8.membrane.annot.beanregistry.BeanRegistryImplementation;
import com.predic8.membrane.core.config.spring.BaseLocationApplicationContext;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.interceptor.FlowController;
import com.predic8.membrane.core.interceptor.GlobalInterceptor;
import com.predic8.membrane.core.kubernetes.client.KubernetesClientFactory;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.RuleManager;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.TransportConfiguration;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpClientFactory;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http.streampump.Statistics;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.DNSCache;
import com.predic8.membrane.core.util.TimerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import java.util.Collection;

public class DefaultMainComponents implements MainComponents {

    private static final Logger log = LoggerFactory.getLogger(DefaultMainComponents.class);

    private final DefaultRouter router;

    private ApplicationContext beanFactory;

    protected BeanRegistry registry;

    protected Transport transport;

    private final TimerManager timerManager = new TimerManager();
    private final HttpClientFactory httpClientFactory = new HttpClientFactory(timerManager);

    private HttpClient httpClient = new HttpClient();

    private final KubernetesClientFactory kubernetesClientFactory = new KubernetesClientFactory(httpClientFactory);
    private ResolverMap resolverMap = new ResolverMap();

    private final FlowController flowController;
    private final RuleManager ruleManager;

    protected final Statistics statistics = new Statistics();


    public DefaultMainComponents(DefaultRouter router) {
        log.debug("Creating new router.");
        this.router = router;
        flowController = new FlowController(router);
        ruleManager= new RuleManager();
        ruleManager.setRouter(router);
    }

    public void init() {
        httpClient = httpClientFactory.createClient(getHttpClientConfig());
        resolverMap = new ResolverMap(httpClient, kubernetesClientFactory);
        resolverMap.addRuleResolver(router);

        log.debug("Initializing.");

        if (registry == null) {
            registry = new BeanRegistryImplementation(null);
            registry.register("router", router);
        }

        registry.registerIfAbsent(HttpClientConfiguration.class, () -> router.getConfiguration().getHttpClientConfig());
        registry.registerIfAbsent(ExchangeStore.class, LimitedMemoryExchangeStore::new);
        registry.registerIfAbsent(DNSCache.class, DNSCache::new);

        // Transport last
        if (transport == null) {
            transport = new HttpTransport();
        }
        applyTransportConfig(router.getConfiguration().getTransportConfig());
        transport.init(router);

    }

    private void applyTransportConfig(TransportConfiguration transportConfig) {
        if (transportConfig == null)
            return;
        if (transport.getTransportConfig() != null)
            throw new ConfigurationException("Transport settings are configured twice: as attributes of <transport> and in <configuration><transport>. Configure them in one place only, preferably in <configuration><transport>.");
        transport.setTransportConfig(transportConfig);
    }

    public void setRules(Collection<Proxy> proxies) {
        getRuleManager().removeAllRules();
        for (Proxy proxy : proxies)
            getRuleManager().addProxy(proxy, RuleManager.RuleDefinitionSource.SPRING);
    }

    @Override
    public RuleManager getRuleManager() {
        return ruleManager;
    }

    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        beanFactory = ctx;
        if (ctx instanceof BaseLocationApplicationContext blac)
            router.getConfiguration().setBaseLocation(blac.getBaseLocation());
    }

    public void setRuleManager(RuleManager ruleManager) {
        log.debug("Setting ruleManager.");
        ruleManager.setRouter(router);
        getRegistry().register("ruleManager", ruleManager);
    }

    @Override
    public ExchangeStore getExchangeStore() {
        return getRegistry().getBean(ExchangeStore.class).orElseThrow();
    }

    public void setExchangeStore(ExchangeStore exchangeStore) {
        getRegistry().register("exchangeStore", exchangeStore);
    }

    @Override
    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    @Override
    public DNSCache getDnsCache() {
        return getRegistry().getBean(DNSCache.class).orElseThrow(); // TODO
    }

    @Override
    public ResolverMap getResolverMap() {
        return resolverMap;
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    public void setGlobalInterceptor(GlobalInterceptor globalInterceptor) {
        getRegistry().register("globalInterceptor", globalInterceptor);
    }

    @Override
    public TimerManager getTimerManager() {
        return timerManager;
    }

    @Override
    public KubernetesClientFactory getKubernetesClientFactory() {
        return kubernetesClientFactory;
    }

    public HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    @Override
    public HttpClientConfiguration getHttpClientConfig() {
        return router.getConfiguration().getHttpClientConfig();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public FlowController getFlowController() {
        return flowController;
    }

    public void setRegistry(BeanRegistry registry) {
        this.registry = registry;
    }

    public BeanRegistry getRegistry() {
        if (registry == null) {
            registry = new BeanRegistryImplementation(null);
            registry.register("router", router);
        }
        return registry;
    }

    @Override
    public ApplicationContext getBeanFactory() {
        return beanFactory;
    }
}
