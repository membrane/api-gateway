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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;

public class RuleManager {

	private static Log log = LogFactory.getLog(RuleManager.class.getName());

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

		router.getTransport().openPort(rule.getKey().getIp(), rule.getKey().getPort(), rule.getSslInboundContext());

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
		for (Rule rule : rules) {
			router.getTransport().openPort(rule.getKey().getIp(), rule.getKey().getPort(), rule.getSslInboundContext());
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

	public Rule getMatchingRule(String hostHeader, String method, String uri, int port, String localIP) {
		for (Rule rule : rules) {

			log.debug("Host from rule: " + rule.getKey().getHost() + ";   Host from parameter rule key: " + hostHeader);
			
			if (!rule.isActive())
				continue;
			
			if (rule.getKey().getIp() != null)
				if (!rule.getKey().getIp().equals(localIP))
					continue;

			if (!rule.getKey().matchesHostHeader(hostHeader))
				continue;
			if (rule.getKey().getPort() != -1 && port != -1 && rule.getKey().getPort() != port)
				continue;
			if (!rule.getKey().getMethod().equals(method) && !rule.getKey().isMethodWildcard())
				continue;

			if (!rule.getKey().isUsePathPattern())
				return rule;

			if (rule.getKey().matchesPath(uri))
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
			public Rule set(int index, Rule element) {
				throw new IllegalStateException("set(int, Rule) is not allowed");
			}
			public boolean add(Rule e) {
				addProxy(e, source);
				return super.add(e);
			}
			public void add(int index, Rule e) {
				addProxy(e, source);
				super.add(index, e);
			}
		};
		return res;
	}

}
