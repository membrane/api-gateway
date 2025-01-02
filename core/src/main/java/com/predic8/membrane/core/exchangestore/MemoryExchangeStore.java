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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.rules.*;

import java.util.*;

/**
 * TODO: thread-safety
 *
 * @description Stores all exchanges in-memory. The Java heap will overflow if this store is used to store too many
 *              Exchanges. Use for Membrane Monitor only.
 */
@MCElement(name="memoryExchangeStore")
public class MemoryExchangeStore extends AbstractExchangeStore {

	private final Map<RuleKey, List<AbstractExchange>> exchangesMap = new HashMap<>();

	//for synchronization purposes choose Vector class
	private final List<AbstractExchange> totals = new Vector<>();

	public void snap(AbstractExchange exc, Flow flow) {
		// TODO: [fix me] this is for Membrane Monitor's legacy logic

		if (flow != Flow.REQUEST)
			return;

		if (isKeyInStore(exc)) {
			getKeyList(exc).add(exc);
		} else {
			List<AbstractExchange> list = new Vector<>();
			list.add(exc);
			exchangesMap.put(exc.getProxy().getKey(), list);
		}

		totals.add(exc);

		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			exc.addExchangeStoreListener(listener);
			listener.addExchange(exc.getProxy(), exc);
		}
	}

	public void remove(AbstractExchange exc) {
		removeWithoutNotify(exc);

		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.removeExchange(exc);
		}
	}

	public void removeAllExchanges(Proxy proxy) {
		AbstractExchange[] exchanges = getExchanges(proxy.getKey());

		exchangesMap.remove(proxy.getKey());
		totals.removeAll(Arrays.asList(exchanges));
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.removeExchanges(proxy, exchanges);
		}
	}

	public AbstractExchange[] getExchanges(RuleKey ruleKey) {
		List<AbstractExchange> exchangesList = exchangesMap.get(ruleKey);
		if (exchangesList == null) {
			return new AbstractExchange[0];
		}
		return exchangesList.toArray(new AbstractExchange[0]);
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

        for (AbstractExchange abstractExchange : exchangesList) statistics.collectFrom(abstractExchange);

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
		return exchangesMap.get(exc.getProxy().getKey());
	}

	private boolean isKeyInStore(AbstractExchange exc) {
		return exchangesMap.containsKey(exc.getProxy().getKey());
	}

	private void removeWithoutNotify(AbstractExchange exc) {
		if (!isKeyInStore(exc)) {
			return;
		}

		getKeyList(exc).remove(exc);
		if (getKeyList(exc).isEmpty()) {
			exchangesMap.remove(exc.getProxy().getKey());
		}
		totals.remove(exc);
		exc.informExchangeViewerOnRemoval();
	}

}
