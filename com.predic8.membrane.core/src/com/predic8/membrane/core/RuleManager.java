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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;

public class RuleManager {

	public static final int EXCHANGE_STORE_NUMBER = 1000;

	private ExchangeStore exchangeStore = new ForgetfulExchangeStore();
	
	private Map<RuleKey, Rule> rules = new HashMap<RuleKey, Rule>();
	private Set<IRuleChangeListener> tableViewerListeners = new HashSet<IRuleChangeListener>();

	private String defaultTargetHost = "localhost";
	private String defaultHost = "*";
	private String defaultListenPort = "2000";
	private String defaultTargetPort = "8080";
	private String defaultPath = ".*";
	private int defaultMethod = 0;
	private boolean defaultBlockRequest;
	private boolean defaultBlockResponse;

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

	public void setDefaultIsRequestBlocked(boolean bool) {
		defaultBlockRequest = bool;
	}

	public void setDefaultIsResponseBlocked(boolean bool) {
		defaultBlockResponse = bool;
	}

	public boolean isAnyRuleWithPort(int port) {
		Set<RuleKey> ruleKeys = rules.keySet();
		for (RuleKey key : ruleKeys) {
			if (key.getPort() == port) {
				return true;
			}
		}
		return false;
	}

	public synchronized void addRuleIfNew(Rule rule) {

		if (rules.containsKey(rule.getRuleKey()))
			return;
		
		rule.setBlockRequest(defaultBlockRequest);
		rule.setBlockResponse(defaultBlockResponse);
		rules.put(rule.getRuleKey(), rule);

		for (IRuleChangeListener listener : tableViewerListeners) {
			listener.addRule(rule);
		}

		exchangeStore.notifyListenersOnRuleAdd(rule);
	}

	public Set<RuleKey> getRuleKeys() {
		return rules.keySet();
	}

	public Collection<Rule> getRules() {
		return rules.values();
	}

	public void ruleChanged(Rule rule) {
		for (IRuleChangeListener listener : tableViewerListeners) {
			listener.updateRule(rule);
		}
		exchangeStore.refreshAllTreeViewers();
	}

	public Rule getRule(RuleKey ruleKey) {
		return rules.get(ruleKey);
	}

	public Rule getMatchingRule(RuleKey ruleKey) {
		if (rules.get(ruleKey) != null)
			return rules.get(ruleKey);

		Set<RuleKey> ruleKeys = rules.keySet();
		for (RuleKey key : ruleKeys) {
			if (!key.getHost().equals(ruleKey.getHost()) && !key.isHostWildcard())
				continue;
			if (key.getPort() != ruleKey.getPort())
				continue;
			if (!key.getMethod().equals(ruleKey.getMethod()) && !key.isMethodWildcard())
				continue;

			if (!key.isUsePathPattern()) 
				return rules.get(key);
			
			if (key.isPathRegExp()) {
				Pattern p = Pattern.compile(key.getPath());
				if (p.matcher(ruleKey.getPath()).find())
					return rules.get(key);
			} else {
				if (ruleKey.getPath().indexOf(key.getPath()) >=0 )
					return rules.get(key);
			}
		}
		return null;
	}

	public void addTableViewerListener(IRuleChangeListener viewer) {
		tableViewerListeners.add(viewer);

	}

	public void removeTableViewerListener(IRuleChangeListener viewer) {
		tableViewerListeners.remove(viewer);

	}

	public void addTreeViewerListener(IRuleTreeViewerListener viewer) {
		exchangeStore.addTreeViewerListener(viewer);

	}

	public void removeTreeViewerListener(IRuleTreeViewerListener viewer) {
		exchangeStore.removeTreeViewerListener(viewer);
	}

	public synchronized void removeRule(Rule rule) {
		exchangeStore.removeAllExchanges(rule);
		rules.remove(rule.getRuleKey());

		for (IRuleChangeListener listener : tableViewerListeners) {
			listener.removeRule(rule);
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
			if (rule.getRuleKey().getPort() == port)
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
