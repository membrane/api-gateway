/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.rules.Proxy;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class RuleManager {

    private static final Logger log = LoggerFactory.getLogger(RuleManager.class.getName());

    private Router router;

    private final List<Proxy> proxies = new Vector<>();
    private final List<RuleDefinitionSource> ruleSources = new ArrayList<>();
    private final Set<IRuleChangeListener> listeners = new HashSet<>();

    public enum RuleDefinitionSource {
        /**
         * rule defined in the spring context that created the router
         */
        SPRING,
        /**
         * rule defined by admin web interface or through custom code
         */
        MANUAL,
    }

    public boolean isAnyRuleWithPort(int port) {
        for (Proxy proxy : proxies) {
            if (proxy.getKey().getPort() == port) {
                return true;
            }
        }
        return false;
    }

    public void addProxyAndOpenPortIfNew(SSLableProxy proxy) throws IOException {
        addProxyAndOpenPortIfNew(proxy, RuleDefinitionSource.MANUAL);
    }

    public synchronized void addProxyAndOpenPortIfNew(SSLableProxy proxy, RuleDefinitionSource source) throws IOException {
        if (exists(proxy.getKey()))
            return;

        router.getTransport().openPort(proxy, router.getTimerManager());

        proxies.add(proxy);
        ruleSources.add(source);

        for (IRuleChangeListener listener : listeners) {
            listener.ruleAdded(proxy);
        }
    }

    public synchronized void addProxy(Proxy proxy, RuleDefinitionSource source) {
        if (exists(proxy.getKey()))
            return;

        proxies.add(proxy);
        ruleSources.add(source);

        for (IRuleChangeListener listener : listeners) {
            listener.ruleAdded(proxy);
        }
    }

    public synchronized void openPorts() throws IOException {
        HashMap<IpPort, SSLProvider> sslProviders = getSslProviders();

        for (Proxy proxy : proxies) {

            if (proxy instanceof NotPortOpeningProxy)
                continue;

            if (proxy.getName().contains("/")) {
                log.error("API name is {}. <api> names must not contain a '/'. ", proxy.getName());
            }

            router.getTransport().openPort(proxy.getKey().getIp(), proxy.getKey().getPort(), sslProviders.get(getIpPort(proxy)),
                    router.getTimerManager());
        }
    }

    private static @NotNull IpPort getIpPort(Proxy proxy) throws UnknownHostException {
        return new IpPort(proxy.getKey().getIp(), proxy.getKey().getPort());
    }

    private @NotNull HashMap<IpPort, SSLProvider> getSslProviders() throws UnknownHostException {
        HashMap<IpPort, SSLProvider> p = new HashMap<>();
        for (Map.Entry<IpPort, SSLContextCollection.Builder> e : getSSLContexts().entrySet())
            p.put(e.getKey(), e.getValue().build());
        return p;
    }

    private @NotNull HashMap<IpPort, SSLContextCollection.Builder> getSSLContexts() throws UnknownHostException {
        HashMap<IpPort, SSLContextCollection.Builder> sslContexts = new HashMap<>();
        for (Proxy proxy : proxies) {
            if (!(proxy instanceof SSLableProxy sp))
                continue;

            SSLContext sslContext = sp.getSslInboundContext();
            if (sslContext == null)
                continue;

            IpPort ipPort = getIpPort(sp);
            SSLContextCollection.Builder builder = sslContexts.get(ipPort);
            if (builder == null) {
                builder = new SSLContextCollection.Builder();
                sslContexts.put(ipPort, builder);
            }
            builder.add(sslContext);

        }
        return sslContexts;
    }


    public boolean exists(RuleKey key) {
        return getRule(key) != null;
    }

    private Proxy getRule(RuleKey key) {
        for (Proxy r : proxies) {
            if (r.getKey().equals(key))
                return r;
        }
        return null;
    }

    public List<Proxy> getRules() {
        return proxies;
    }

    public void ruleChanged(Proxy proxy) {
        for (IRuleChangeListener listener : listeners) {
            listener.ruleUpdated(proxy);
        }
        getExchangeStore().refreshExchangeStoreListeners();
    }

    public Proxy getMatchingRule(Exchange exc) {
        Request request = exc.getRequest();

        String hostHeader = request.getHeader().getHost();
        String method = request.getMethod();
        String uri = request.getUri();
        String version = request.getVersion();

        AbstractHttpHandler handler = exc.getHandler();
        int port = handler.isMatchLocalPort() ? handler.getLocalPort() : -1;
        String localIP = handler.getLocalAddress().getHostAddress();

        for (Proxy proxy : proxies) {
            RuleKey key = proxy.getKey();

            log.debug("Host from rule: {} Host from parameter rule key: {}", key.getHost(), hostHeader);

            if (!proxy.isActive())
                continue;
            if (!key.matchesVersion(version))
                continue;
            if (key.getIp() != null && !key.getIp().equals(localIP))
                continue;
            if (!key.matchesHostHeader(hostHeader))
                continue;
            if (key.getPort() != -1 && port != -1 && key.getPort() != port)
                continue;
            if (!key.getMethod().equals(method) && !key.isMethodWildcard())
                continue;
            if (key.isUsePathPattern() && !key.matchesPath(uri))
                continue;
            if (!key.complexMatch(exc))
                continue;

            if (log.isDebugEnabled())
                log.debug("Matching Rule found for RuleKey {} {} {} {} {}", hostHeader, method, uri, port, localIP);
            return proxy;
        }
        return findProxyRule(exc);
    }

    private Proxy findProxyRule(Exchange exc) {
        for (Proxy proxy : getRules()) {
            if (!(proxy instanceof ProxyRule))
                continue;

            if (proxy.getKey().getIp() != null)
                if (!proxy.getKey().getIp().equals(exc.getHandler().getLocalAddress().toString()))
                    continue;


            if (proxy.getKey().getPort() == -1 || exc.getHandler().getLocalPort() == -1 || proxy.getKey().getPort() == exc.getHandler().getLocalPort()) {
                if (log.isDebugEnabled())
                    log.debug("proxy rule found: {}", proxy);
                return proxy;
            }
        }
        log.debug("No rule found for incoming request");
        return new NullProxy();
    }

    public void addRuleChangeListener(IRuleChangeListener viewer) {
        listeners.add(viewer);
        viewer.batchUpdate(proxies.size());
    }

    public void removeRuleChangeListener(IRuleChangeListener viewer) {
        listeners.remove(viewer);

    }

    public void addExchangesStoreListener(IExchangesStoreListener viewer) {
        getExchangeStore().addExchangesStoreListener(viewer);

    }

    public void removeExchangesStoreListener(IExchangesStoreListener viewer) {
        getExchangeStore().removeExchangesStoreListener(viewer);
    }

    public synchronized void removeRule(Proxy proxy) {
        getExchangeStore().removeAllExchanges(proxy);

        int i = proxies.indexOf(proxy);
        proxies.remove(i);
        ruleSources.remove(i);

        for (IRuleChangeListener listener : listeners) {
            listener.ruleRemoved(proxy, proxies.size());
        }

    }

    public synchronized void replaceRule(Proxy proxy, Proxy newProxy) {
        getExchangeStore().removeAllExchanges(proxy);

        int i = proxies.indexOf(proxy);
        proxies.set(i, newProxy);

        for (IRuleChangeListener listener : listeners) {
            listener.ruleRemoved(proxy, proxies.size());
        }
        for (IRuleChangeListener listener : listeners) {
            listener.ruleAdded(newProxy);
        }
    }

    public synchronized void removeAllRules() {
        while (!proxies.isEmpty())
            removeRule(proxies.getFirst());
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    private ExchangeStore getExchangeStore() {
        return router.getExchangeStore();
    }

    public Proxy getRuleByName(String name) {
        for (Proxy r : proxies) {
            if (name.equals(r.getName())) return r;
        }
        return null;
    }

    public synchronized List<Proxy> getRulesBySource(final RuleDefinitionSource source) {
        return new ArrayList<>() {
            @Serial
            private static final long serialVersionUID = 1L;

            {
                for (int i = 0; i < proxies.size(); i++)
                    if (ruleSources.get(i) == source)
                        add(proxies.get(i));
            }

            @Override
            public Proxy set(int index, Proxy element) {
                throw new IllegalStateException("set(int, Rule) is not allowed");
            }

            @Override
            public boolean add(Proxy e) {
                addProxy(e, source);
                return super.add(e);
            }

            @Override
            public void add(int index, Proxy e) {
                addProxy(e, source);
                super.add(index, e);
            }
        };
    }

}
