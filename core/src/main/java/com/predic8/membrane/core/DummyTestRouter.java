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

package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import org.springframework.context.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.MANUAL;

public class DummyTestRouter extends AbstractRouter {

    private FlowController flowController = new FlowController(this);
    private ExchangeStore exchangeStore = new LimitedMemoryExchangeStore();
    private RuleManager ruleManager = new RuleManager();

    private final TimerManager timerManager = new TimerManager();
    private final HttpClientFactory httpClientFactory = new HttpClientFactory(timerManager);
    private final KubernetesClientFactory kubernetesClientFactory = new KubernetesClientFactory(httpClientFactory);
    private ResolverMap resolverMap = new ResolverMap(httpClientFactory, kubernetesClientFactory);

    private HttpTransport transport;

    private DNSCache dnsCache = new DNSCache();

    private URIFactory uriFactory = new URIFactory();

    private final Statistics statistics = new Statistics();
    private ApplicationContext applicationContext;

    private String baseLocation;

    private Configuration configuration = new Configuration();

    public DummyTestRouter() {
        this(null);
    }

    public DummyTestRouter(ProxyConfiguration proxyConfiguration) {
        transport = createTransport();
        if (proxyConfiguration != null)
            getResolverMap().getHTTPSchemaResolver().getHttpClientConfig().setProxy(proxyConfiguration);
    }

    @Override
    public void init() {
        initProxies();
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
    public FlowController getFlowController() {
        return flowController;
    }

    @Override
    public ExchangeStore getExchangeStore() {
        return exchangeStore;
    }

    @Override
    public RuleManager getRuleManager() {
        return ruleManager;
    }

    @Override
    public String getBaseLocation() {
        return baseLocation;
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
    public HttpTransport getTransport() {
        return transport;
    }

    @Override
    public URIFactory getUriFactory() {
        return uriFactory;
    }

    @Override
    public ApplicationContext getBeanFactory() {
        return applicationContext;
    }

    @Override
    public HttpClientFactory getHttpClientFactory() {
        return null;
    }

    @Override
    public TimerManager getTimerManager() {
        return timerManager;
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    /**
     * Same as the default config from monitor-beans.xml
     *
     * TODO: Sync somehow with standard transport order maybe TransportConfig class or in Transport?
     */
    private static HttpTransport createTransport() {
        HttpTransport transport = new HttpTransport();
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(new RuleMatchingInterceptor());
        interceptors.add(new DispatchingInterceptor());
        interceptors.add(new UserFeatureInterceptor());
        interceptors.add(new InternalRoutingInterceptor());
        HTTPClientInterceptor httpClientInterceptor = new HTTPClientInterceptor();
        interceptors.add(httpClientInterceptor);
        transport.setFlow(interceptors);
        return transport;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    public void setExchangeStore(ExchangeStore exchangeStore) {
        this.exchangeStore = exchangeStore;
    }

    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        throw new UnsupportedOperationException();
    }

    public HttpClientConfiguration getHttpClientConfig() {
       return resolverMap.getHTTPSchemaResolver().getHttpClientConfig();
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setBaseLocation(String baseLocation) {
        this.baseLocation = baseLocation;
    }
}
