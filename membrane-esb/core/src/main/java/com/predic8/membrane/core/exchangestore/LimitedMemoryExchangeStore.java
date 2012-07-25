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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;

public class LimitedMemoryExchangeStore extends AbstractExchangeStore {

	private int maxSize = 1000000;
	private int currentSize;
	
	private final Queue<AbstractExchange> exchanges = new LinkedList<AbstractExchange>();
	
	public synchronized void add(AbstractExchange exc) {
		if (exc.getResponse() == null || exc.getSize() > maxSize)
			return;

		makeSpaceIfNeeded(exc);

		exchanges.offer(exc);
		currentSize += exc.getSize();
	}

	public synchronized void remove(AbstractExchange exc) {
		exchanges.remove(exc);
	}
	
	public synchronized void removeAllExchanges(Rule rule) {
		exchanges.removeAll(getExchangeList(rule.getKey()));
	}

	private synchronized List<AbstractExchange> getExchangeList(RuleKey key) {
		List<AbstractExchange> c = new ArrayList<AbstractExchange>();
		for(AbstractExchange exc : exchanges) {
			if (exc.getRule().equals(key)) {
				c.add(exc);
			}
		}
		return c;
	}

	public synchronized AbstractExchange[] getExchanges(RuleKey ruleKey) {
		return getExchangeList(ruleKey).toArray(new AbstractExchange[0]);
	}

	public synchronized int getNumberOfExchanges(RuleKey ruleKey) {
		return getExchangeList(ruleKey).size();
	}

	public synchronized StatisticCollector getStatistics(RuleKey key) {
		StatisticCollector statistics = new StatisticCollector(false);
		List<AbstractExchange> exchangesList = getExchangeList(key);
		if (exchangesList == null || exchangesList.isEmpty())
			return statistics;

		for (int i = 0; i < exchangesList.size(); i++)
			statistics.collectFrom(exchangesList.get(i));			
		
		return statistics;
	}

	public synchronized Object[] getAllExchanges() {
		return exchanges.toArray(new AbstractExchange[0]);
	}
	
	public synchronized List<AbstractExchange> getAllExchangesAsList() {
		return (LinkedList<AbstractExchange>)exchanges;
	}	
	
	public synchronized void removeAllExchanges(AbstractExchange[] candidates) {
		exchanges.removeAll(Arrays.asList(candidates));
	}
	
	
	public synchronized AbstractExchange getExchangeById(int id) {
		for (AbstractExchange exc : getAllExchangesAsList()) {
			if (exc.hashCode() == id) {
				return exc;
			}
		}
		return null;
	}
	
	public synchronized List<? extends ClientStatistics> getClientStatistics() {
		Map<String, ClientStatisticsCollector> clients = new HashMap<String, ClientStatisticsCollector>();

		for (AbstractExchange exc : getAllExchangesAsList()) {
			if (!clients.containsKey(exc.getSourceHostname())) {
				clients.put(exc.getSourceHostname(), new ClientStatisticsCollector(exc.getSourceHostname()));
			}
			clients.get(exc.getSourceHostname()).collect(exc);
		}
		return new ArrayList<ClientStatistics>(clients.values());
	}
	
	public synchronized int getCurrentSize() {
		return currentSize;
	}
	
	public synchronized Long getOldestTimeResSent() {
		AbstractExchange exc = exchanges.peek();
		return exc == null ? null : exc.getTimeResSent();
	}

	private void makeSpaceIfNeeded(AbstractExchange exc) {
		while (!hasEnoughSpace(exc)) {
			currentSize -= exchanges.poll().getSize();
		}
	}

	private boolean hasEnoughSpace(AbstractExchange exc) {
		return exc.getSize()+currentSize <= maxSize;
	}

	public int getMaxSize() {
		return maxSize;
	}

	public void setMaxSize(int maxSize) {
		this.maxSize = maxSize;
	}

	
	
}
