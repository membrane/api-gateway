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

    private final List<Rule> rules = new Vector<>();
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
        for (Rule rule : rules) {
            if (rule.getKey().getPort() == port) {
                return true;
            }
        }
        return false;
    }

    public void addProxyAndOpenPortIfNew(Rule rule) throws IOException {
        addProxyAndOpenPortIfNew(rule, RuleDefinitionSource.MANUAL);
    }

    public synchronized void addProxyAndOpenPortIfNew(Rule rule, RuleDefinitionSource source) throws IOException {
        if (exists(rule.getKey()))
            return;

        router.getTransport().openPort(rule.getKey().getIp(), rule.getKey().getPort(), rule.getSslInboundContext(),
                router.getTimerManager());

        rules.add(rule);
        ruleSources.add(source);

        for (IRuleChangeListener listener : listeners) {
            listener.ruleAdded(rule);
        }
    }

    public synchronized void addProxy(Rule rule, RuleDefinitionSource source) {
        if (exists(rule.getKey()))
            return;

        rules.add(rule);
        ruleSources.add(source);

        for (IRuleChangeListener listener : listeners) {
            listener.ruleAdded(rule);
        }
    }

    public synchronized void openPorts() throws IOException {
        HashMap<IpPort, SSLProvider> sslProviders;

        sslProviders = new HashMap<>();
        for (Map.Entry<IpPort, SSLContextCollection.Builder> entry : getSSLContexts().entrySet())
            sslProviders.put(entry.getKey(), entry.getValue().build());

        for (Rule rule : rules) {

            if (rule instanceof NotPortOpeningProxy)
                continue;

            if (rule.getName().contains("/")) {
                log.error("API name is {}. <api> names must not contain a '/'. ", rule.getName());
            }

            IpPort ipPort = new IpPort(rule.getKey().getIp(), rule.getKey().getPort());
            router.getTransport().openPort(rule.getKey().getIp(), rule.getKey().getPort(), sslProviders.get(ipPort),
                    router.getTimerManager());
        }
    }

    private @NotNull HashMap<IpPort, SSLContextCollection.Builder> getSSLContexts() throws UnknownHostException {
        HashMap<IpPort, SSLContextCollection.Builder> sslContexts = new HashMap<>();
        for (Rule rule : rules) {
            SSLContext sslContext = rule.getSslInboundContext();
            if (sslContext != null) {
                IpPort ipPort = new IpPort(rule.getKey().getIp(), rule.getKey().getPort());
                SSLContextCollection.Builder builder = sslContexts.get(ipPort);
                if (builder == null) {
                    builder = new SSLContextCollection.Builder();
                    sslContexts.put(ipPort, builder);
                }
                builder.add(sslContext);
            }
        }
        return sslContexts;
    }


    public boolean exists(RuleKey key) {
        return getRule(key) != null;
    }

    private Rule getRule(RuleKey key) {
        for (Rule r : rules) {
            if (r.getKey().equals(key))
                return r;
        }
        return null;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void ruleChanged(Rule rule) {
        for (IRuleChangeListener listener : listeners) {
            listener.ruleUpdated(rule);
        }
        getExchangeStore().refreshExchangeStoreListeners();
    }

    public Rule getMatchingRule(Exchange exc) {
        Request request = exc.getRequest();
        AbstractHttpHandler handler = exc.getHandler();

        String hostHeader = request.getHeader().getHost();
        String method = request.getMethod(); // @TODO examine closer values like "POST  HTTP/1,1"
        String uri = request.getUri();
        String version = request.getVersion();
        int port = handler.isMatchLocalPort() ? handler.getLocalPort() : -1;
        String localIP = handler.getLocalAddress().getHostAddress();

        for (Rule rule : rules) {
            RuleKey key = rule.getKey();

            log.debug("Host from rule: {} Host from parameter rule key: {}", key.getHost(), hostHeader);

            if (!rule.isActive())
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
            return rule;
        }
        return findProxyRule(exc);
    }

    private Rule findProxyRule(Exchange exc) {
        for (Rule rule : getRules()) {
            if (!(rule instanceof ProxyRule))
                continue;

            if (rule.getKey().getIp() != null)
                if (!rule.getKey().getIp().equals(exc.getHandler().getLocalAddress().toString()))
                    continue;


            if (rule.getKey().getPort() == -1 || exc.getHandler().getLocalPort() == -1 || rule.getKey().getPort() == exc.getHandler().getLocalPort()) {
                if (log.isDebugEnabled())
                    log.debug("proxy rule found: {}", rule);
                return rule;
            }
        }
        log.debug("No rule found for incoming request");
        return new NullRule();
    }

    public void addRuleChangeListener(IRuleChangeListener viewer) {
        listeners.add(viewer);
        viewer.batchUpdate(rules.size());
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

    public synchronized void removeRule(Rule rule) {
        getExchangeStore().removeAllExchanges(rule);

        int i = rules.indexOf(rule);
        rules.remove(i);
        ruleSources.remove(i);

        for (IRuleChangeListener listener : listeners) {
            listener.ruleRemoved(rule, rules.size());
        }

    }

    public synchronized void replaceRule(Rule rule, Rule newRule) {
        getExchangeStore().removeAllExchanges(rule);

        int i = rules.indexOf(rule);
        rules.set(i, newRule);

        for (IRuleChangeListener listener : listeners) {
            listener.ruleRemoved(rule, rules.size());
        }
        for (IRuleChangeListener listener : listeners) {
            listener.ruleAdded(newRule);
        }
    }

    public synchronized void removeAllRules() {
        while (!rules.isEmpty())
            removeRule(rules.getFirst());
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    private ExchangeStore getExchangeStore() {
        return router.getExchangeStore();
    }

    public Rule getRuleByName(String name) {
        for (Rule r : rules) {
            if (name.equals(r.getName())) return r;
        }
        return null;
    }

    public synchronized List<Rule> getRulesBySource(final RuleDefinitionSource source) {
        return new ArrayList<>() {
            @Serial
            private static final long serialVersionUID = 1L;

            {
                for (int i = 0; i < rules.size(); i++)
                    if (ruleSources.get(i) == source)
                        add(rules.get(i));
            }

            @Override
            public Rule set(int index, Rule element) {
                throw new IllegalStateException("set(int, Rule) is not allowed");
            }

            @Override
            public boolean add(Rule e) {
                addProxy(e, source);
                return super.add(e);
            }

            @Override
            public void add(int index, Rule e) {
                addProxy(e, source);
                super.add(index, e);
            }
        };
    }

}
