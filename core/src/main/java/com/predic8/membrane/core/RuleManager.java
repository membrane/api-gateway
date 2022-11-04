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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import com.predic8.membrane.core.rules.InternalProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.config.ConfigurationException;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.transport.http.IpPort;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLContextCollection;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

public class RuleManager {

	private static Logger log = LoggerFactory.getLogger(RuleManager.class.getName());

	private Router router;

	private List<Rule> rules = new Vector<Rule>();
	private List<RuleDefinitionSource> ruleSources = new ArrayList<RuleManager.RuleDefinitionSource>();
	private Set<IRuleChangeListener> listeners = new HashSet<IRuleChangeListener>();

	private String defaultTargetHost = "localhost";
	private String defaultHost = "*";
	private int defaultListenPort = 2000;
	private int defaultTargetPort = 8080;
	private String defaultPath = ".*";
	private int defaultMethod = 4;

	public enum RuleDefinitionSource {
		/** rule defined in the spring context that created the router */
		SPRING,
		/** rule defined by admin web interface or through custom code */
		MANUAL,
	}

	public int getDefaultListenPort() {
		return defaultListenPort;
	}

	public String getDefaultHost() {
		return defaultHost;
	}

	public String getDefaultPath() {
		return defaultPath;
	}

	public int getDefaultMethod() {
		return defaultMethod;
	}

	public void setDefaultMethod(int defaultMethod) {
		this.defaultMethod = defaultMethod;
	}

	public String getDefaultTargetHost() {
		return defaultTargetHost;
	}

	public void setDefaultTargetHost(String defaultTargetHost) {
		this.defaultTargetHost = defaultTargetHost;
	}

	public int getDefaultTargetPort() {
		return defaultTargetPort;
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

		if (!(rule instanceof InternalProxy))
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
		try {
			HashMap<IpPort, SSLContextCollection.Builder> sslContexts = new HashMap<IpPort, SSLContextCollection.Builder>();
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

			sslProviders = new HashMap<IpPort, SSLProvider>();
			for (Map.Entry<IpPort, SSLContextCollection.Builder> entry : sslContexts.entrySet())
				sslProviders.put(entry.getKey(), entry.getValue().build());
		} catch (ConfigurationException e) {
			throw new IOException(e);
		}

		for (Rule rule : rules) {
			if(rule instanceof InternalProxy) {
				if (rule.getName().contains("/"))
					throw new RuntimeException("<internalProxy> names must not contain a '/'.");
				continue;
			}

			IpPort ipPort = new IpPort(rule.getKey().getIp(), rule.getKey().getPort());
			router.getTransport().openPort(rule.getKey().getIp(), rule.getKey().getPort(), sslProviders.get(ipPort),
					router.getTimerManager());
		}
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

	public synchronized void ruleUp(Rule rule) {
		int index = rules.indexOf(rule);
		if (index <= 0)
			return;
		Collections.swap(rules, index, index - 1);
		Collections.swap(ruleSources, index, index - 1);
		for (IRuleChangeListener listener : listeners) {
			listener.rulePositionsChanged();
		}
	}

	public synchronized void ruleDown(Rule rule) {
		int index = rules.indexOf(rule);
		if (index < 0 || index == (rules.size() - 1))
			return;
		Collections.swap(rules, index, index + 1);
		Collections.swap(ruleSources, index, index + 1);
		for (IRuleChangeListener listener : listeners) {
			listener.rulePositionsChanged();
		}
	}

	public void ruleChanged(Rule rule) {
		for (IRuleChangeListener listener : listeners) {
			listener.ruleUpdated(rule);
		}
		getExchangeStore().refreshExchangeStoreListeners();
	}

	public Rule getMatchingRule(String hostHeader, String method, String uri, String version, int port, String localIP) {
		for (Rule rule : rules) {
			RuleKey key = rule.getKey();

			log.debug("Host from rule: " + key.getHost() + ";   Host from parameter rule key: " + hostHeader);

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
			if (!key.complexMatch(hostHeader, method, uri, version, port, localIP))
				continue;

			return rule;
		}
		return null;
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

	public synchronized void removeRulesFromSource(RuleDefinitionSource source) {
		for (int i = 0; i < rules.size(); i++)
			if (ruleSources.get(i) == source)
				removeRule(rules.get(i--));
	}

	public synchronized void removeAllRules() {
		while (rules.size() > 0)
			removeRule(rules.get(0));
	}

	public synchronized int getNumberOfRules() {
		return rules.size();
	}

	public void setRouter(Router router) {
		this.router = router;
	}

	private ExchangeStore getExchangeStore() {
		return router.getExchangeStore();
	}

	public Rule getRuleByName(String name) {
		for (Rule r : rules ) {
			if ( name.equals(r.getName()) ) return r;
		}
		return null;
	}

	public synchronized List<Rule> getRulesBySource(final RuleDefinitionSource source) {
		ArrayList<Rule> res = new ArrayList<Rule>() {
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
		return res;
	}

}
