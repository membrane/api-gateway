/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.model.IExchangesViewListener;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;

public class RuleManager {

	public static final int EXCHANGE_STORE_NUMBER = 1000;

	private ExchangeStore exchangeStore = new ForgetfulExchangeStore();
	
	private List<Rule> rules = new Vector<Rule>();
	private Set<IRuleChangeListener> listeners = new HashSet<IRuleChangeListener>();

	private String defaultTargetHost = "localhost";
	private String defaultHost = "*";
	private String defaultListenPort = "2000";
	private String defaultTargetPort = "8080";
	private String defaultPath = ".*";
	private int defaultMethod = 0;
	
	public String getDefaultListenPort() {
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

	public String getDefaultTargetPort() {
		return defaultTargetPort;
	}

	public void setDefaultTargetPort(int targetPort) {
		if (targetPort < 1 || targetPort > 65535)
			defaultTargetPort = "";
		else
			defaultTargetPort = Integer.toString(targetPort);
	}

	public boolean isAnyRuleWithPort(int port) {
		for (Rule rule : rules) {
			if (rule.getKey().getPort() == port) {
				return true;
			}
		}
		return false;
	}
	
	public synchronized void addRuleIfNew(Rule rule) {
		if (exists(rule.getKey()))
			return;
		
		rules.add(rule);

		for (IRuleChangeListener listener : listeners) {
			listener.ruleAdded(rule);
		}

		exchangeStore.notifyListenersOnRuleAdd(rule);
	}

	public boolean exists(RuleKey key) {
		for (Rule r : rules) {
			if (r.getKey().equals(key))
				return true;
		}
		return false;
	}
	
	private Rule getRule(RuleKey key) {
		for (Rule r : rules) {
			if (r.getKey().equals(key))
				return r;
		}
		throw new IllegalArgumentException("There is no rule with this key");
	}

	public List<Rule> getRules() {
		return rules;
	}

	public void ruleUp(Rule rule) {
		int index = rules.indexOf(rule);
		if (index <= 0 )
			return;
		Collections.swap(rules, index, index - 1);
	}
	
	public void ruleDown(Rule rule) {
		int index = rules.indexOf(rule);
		if (index < 0 || index == rules.size() - 1 )
			return;
		Collections.swap(rules, index, index + 1);
	}
	
	public void ruleChanged(Rule rule) {
		for (IRuleChangeListener listener : listeners) {
			listener.ruleUpdated(rule);
		}
		exchangeStore.refreshAllTreeViewers();
	}

	public Rule getMatchingRule(RuleKey ruleKey) {
		if (exists(ruleKey))
			return getRule(ruleKey);

		for (Rule rule : rules) {
			if (!rule.getKey().getHost().equals(ruleKey.getHost()) && !rule.getKey().isHostWildcard())
				continue;
			if (rule.getKey().getPort() != ruleKey.getPort())
				continue;
			if (!rule.getKey().getMethod().equals(ruleKey.getMethod()) && !rule.getKey().isMethodWildcard())
				continue;

			if (!rule.getKey().isUsePathPattern()) 
				return rule;
			
			if (rule.getKey().isPathRegExp()) {
				Pattern p = Pattern.compile(rule.getKey().getPath());
				if (p.matcher(ruleKey.getPath()).find())
					return rule;
			} else {
				if (ruleKey.getPath().indexOf(rule.getKey().getPath()) >=0 )
					return rule;
			}
		}
		return null;
	}

	public void addTableViewerListener(IRuleChangeListener viewer) {
		listeners.add(viewer);

	}

	public void removeTableViewerListener(IRuleChangeListener viewer) {
		listeners.remove(viewer);

	}

	public void addTreeViewerListener(IExchangesViewListener viewer) {
		exchangeStore.addExchangesViewListener(viewer);

	}

	public void removeTreeViewerListener(IExchangesViewListener viewer) {
		exchangeStore.removeExchangesViewListener(viewer);
	}

	public synchronized void removeRule(Rule rule) {
		exchangeStore.removeAllExchanges(rule);
		rules.remove(rule);

		for (IRuleChangeListener listener : listeners) {
			listener.ruleRemoved(rule);
		}

		exchangeStore.notifyListenersOnRuleRemoval(rule, rules.size());

	}

	public synchronized void removeAllRules() {
		try {
			Collection<Rule> rules = getRules();
			if (rules == null || rules.size() == 0) 
				return;
			
			List<Rule> rulesCopy = new ArrayList<Rule>(rules);
			for (Rule rule : rulesCopy) {
				removeRule(rule);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized int getTotalNumberOfRules() {
		return rules.size();
	}

	public synchronized void removeRuleByPort(int port) {
		Collection<Rule> rules = getRules();
		for (Rule rule : rules) {
			if (rule.getKey().getPort() == port)
				removeRule(rule);
		}
	}

	public ExchangeStore getExchangeStore() {
		return exchangeStore;
	}

	public void setExchangeStore(ExchangeStore exchangeStore) {
		this.exchangeStore = exchangeStore;
	}

	
}
