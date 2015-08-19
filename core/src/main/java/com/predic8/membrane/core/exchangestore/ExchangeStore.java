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

import java.util.List;



import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;

public interface ExchangeStore {


	public void addExchangesStoreListener(IExchangesStoreListener viewer);

	public void removeExchangesStoreListener(IExchangesStoreListener viewer);

	public void refreshExchangeStoreListeners();

	public void notifyListenersOnExchangeAdd(Rule rule, AbstractExchange exchange);

	public void notifyListenersOnExchangeRemoval(AbstractExchange exchange);

	/**
	 * Adds the current state of the exchange to the store.
	 *
	 * Implementations should take a snapshot of the current state of the request (or response) headers and register a
	 * body observer in which they will be called back as soon as the body has fully been received.
	 *
	 * If flow==REQUEST, the request is added. Elsewise, the response is added (if present).
	 */
	public void snap(AbstractExchange exchange, Flow flow);

	public void remove(AbstractExchange exchange);

	public void removeAllExchanges(Rule rule);

	public void removeAllExchanges(AbstractExchange[] exchanges);

	public AbstractExchange[] getExchanges(RuleKey ruleKey);

	public int getNumberOfExchanges(RuleKey ruleKey);

	public StatisticCollector getStatistics(RuleKey ruleKey);

	public Object[] getAllExchanges();

	public List<AbstractExchange> getAllExchangesAsList();

	public AbstractExchange getExchangeById(int id);

	public void init() throws Exception;

	public List<? extends ClientStatistics> getClientStatistics();

	public void collect(ExchangeCollector col);

	long getLastModified();
	/**
	 * Returns immediately if lastKnownModification is smaller than last known modification.
	 * Otherwise it waits until a modification occurs.
	 */
	void waitForModification(long lastKnownModification);
}
