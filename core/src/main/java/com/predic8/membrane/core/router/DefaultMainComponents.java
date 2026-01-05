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
import com.predic8.membrane.core.config.spring.*;
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
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.context.*;

import java.util.*;

public class DefaultMainComponents implements MainComponents {

    private static final Logger log = LoggerFactory.getLogger(DefaultMainComponents.class);

    private final DefaultRouter router;

    private ApplicationContext beanFactory;

    protected BeanRegistry registry;

    protected Transport transport;

    private final TimerManager timerManager = new TimerManager();
    private final HttpClientFactory httpClientFactory = new HttpClientFactory(timerManager);
    private final KubernetesClientFactory kubernetesClientFactory = new KubernetesClientFactory(httpClientFactory);
    private ResolverMap resolverMap;

    private FlowController flowController;
    private RuleManager ruleManager;

    protected final Statistics statistics = new Statistics();


    public DefaultMainComponents(DefaultRouter router) {
        log.debug("Creating new router.");
        this.router = router;
        resolverMap = new ResolverMap(httpClientFactory, kubernetesClientFactory);
        resolverMap.addRuleResolver(router);
        flowController = new FlowController(router);
        ruleManager= new RuleManager();
        ruleManager.setRouter(router);
    }

    public void init() {
        log.debug("Initializing.");

        if (registry == null) {
            registry = new BeanRegistryImplementation(null, router, null);
        }

        registry.registerIfAbsent(HttpClientConfiguration.class, HttpClientConfiguration::new);
        registry.registerIfAbsent(ExchangeStore.class, LimitedMemoryExchangeStore::new);
        registry.registerIfAbsent(DNSCache.class, DNSCache::new);

        // Transport last
        if (transport == null) {
            transport = new HttpTransport();
        }
        transport.init(router);

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

    public HttpClientConfiguration getHttpClientConfig() {
        return getResolverMap().getHTTPSchemaResolver().getHttpClientConfig();
    }

    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        getResolverMap().getHTTPSchemaResolver().setHttpClientConfig(httpClientConfig);
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

    public FlowController getFlowController() {
        return flowController;
    }

    public void setRegistry(BeanRegistry registry) {
        this.registry = registry;
    }

    public BeanRegistry getRegistry() {
        if (registry == null)
            registry = new BeanRegistryImplementation(null, router, null);
        return registry;
    }

    @Override
    public ApplicationContext getBeanFactory() {
        return beanFactory;
    }
}
