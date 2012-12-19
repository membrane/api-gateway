/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchangestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;

/**
 * TODO: thread-safety
 */
public class MemoryExchangeStore extends AbstractExchangeStore {

	private Map<RuleKey, List<AbstractExchange>> exchangesMap = new HashMap<RuleKey, List<AbstractExchange>>();

	//for synchronization purposes choose Vector class
	private List<AbstractExchange> totals = new Vector<AbstractExchange>();  
	
	public void add(AbstractExchange exc) {
		
		if (exc.getResponse() != null)
			return;
		
		if (isKeyInStore(exc)) {
			getKeyList(exc).add(exc);
		} else {
			List<AbstractExchange> list = new Vector<AbstractExchange>();
			list.add(exc);
			exchangesMap.put(exc.getRule().getKey(), list);
		}
		
		totals.add(exc);
		
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			exc.addExchangeStoreListener(listener);
			listener.addExchange(exc.getRule(), exc);
		}
	}

	public void remove(AbstractExchange exc) {
		removeWithoutNotify(exc);
		
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.removeExchange(exc);
		}
	}
	
	public void removeAllExchanges(Rule rule) {
		AbstractExchange[] exchanges = getExchanges(rule.getKey());
		
		exchangesMap.remove(rule.getKey());
		totals.removeAll(Arrays.asList(exchanges));
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.removeExchanges(rule, exchanges);
		}
	}

	public AbstractExchange[] getExchanges(RuleKey ruleKey) {
		List<AbstractExchange> exchangesList = exchangesMap.get(ruleKey);
		if (exchangesList == null) {
			return new AbstractExchange[0];
		}
		return exchangesList.toArray(new AbstractExchange[exchangesList.size()]);
	}

	public int getNumberOfExchanges(RuleKey ruleKey) {
		if (!exchangesMap.containsKey(ruleKey)) {
			return 0;
		}

		return exchangesMap.get(ruleKey).size();
	}

	public StatisticCollector getStatistics(RuleKey key) {
		StatisticCollector statistics = new StatisticCollector(false);
		List<AbstractExchange> exchangesList = exchangesMap.get(key);
		if (exchangesList == null || exchangesList.isEmpty())
			return statistics;

		for (int i = 0; i < exchangesList.size(); i++)
			statistics.collectFrom(exchangesList.get(i));
		
		return statistics;
	}

	public Object[] getAllExchanges() {
		if (totals.isEmpty()) 
			return null;
		
		return totals.toArray();
	}

	
	public List<AbstractExchange> getAllExchangesAsList() {
		return totals;
	}
	
	
	public void removeAllExchanges(AbstractExchange[] exchanges) {
		for (AbstractExchange exc : exchanges) {
			removeWithoutNotify(exc);
		}
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.removeExchanges(exchanges);
		}
	}
	
	private List<AbstractExchange> getKeyList(AbstractExchange exc) {
		return exchangesMap.get(exc.getRule().getKey());
	}

	private boolean isKeyInStore(AbstractExchange exc) {
		return exchangesMap.containsKey(exc.getRule().getKey());
	}

	private void removeWithoutNotify(AbstractExchange exc) {
		if (!isKeyInStore(exc)) {
			return;
		}

		getKeyList(exc).remove(exc);
		if (getKeyList(exc).isEmpty()) {
			exchangesMap.remove(exc.getRule().getKey());
		}
		totals.remove(exc);
		exc.informExchangeViewerOnRemoval();
	}

}
