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

package com.predic8.membrane.core.exchangestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.statistics.RuleStatistics;

public class MemoryExchangeStore extends AbstractExchangeStore {

	private Map<RuleKey, List<Exchange>> ruleExchangeMap = new HashMap<RuleKey, List<Exchange>>();

	List<Exchange> totalList = new ArrayList<Exchange>();
	
	private int threashold = 1000;

	public void add(Exchange exchange) {
		if (exchange.getResponse() != null) {
			return;
		}

		if (ruleExchangeMap.containsKey(exchange.getRule().getRuleKey())) {
			ruleExchangeMap.get(exchange.getRule().getRuleKey()).add(exchange);
		} else {
			List<Exchange> list = new Vector<Exchange>();
			list.add(exchange);
			ruleExchangeMap.put(exchange.getRule().getRuleKey(), list);
		}
		
		totalList.add(exchange);

		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			exchange.addTreeViewerListener(listener);
			listener.addExchange(exchange.getRule(), exchange);
		}
	}

	public void remove(Exchange exchange) {
		if (!ruleExchangeMap.containsKey(exchange.getRule().getRuleKey())) {
			return;
		}

		ruleExchangeMap.get(exchange.getRule().getRuleKey()).remove(exchange);
		if (ruleExchangeMap.get(exchange.getRule().getRuleKey()).size() == 0) {
			ruleExchangeMap.remove(exchange.getRule().getRuleKey());
		}
		exchange.informExchangeViewerOnRemoval();
		totalList.remove(exchange);
		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			listener.removeExchange(exchange);
		}
	}

	public void removeAllExchanges(Rule rule) {
		if (rule == null || rule.getRuleKey() == null) {
			return;
		}
		Exchange[] exchanges = getExchanges(rule.getRuleKey());
		ruleExchangeMap.remove(rule.getRuleKey());
		totalList.removeAll(Arrays.asList(exchanges));
		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			listener.removeExchanges(rule, exchanges);
		}
	}

	public Exchange[] getExchanges(RuleKey ruleKey) {
		List<Exchange> exchangesList = ruleExchangeMap.get(ruleKey);
		if (exchangesList == null) {
			return new Exchange[0];
		}
		return exchangesList.toArray(new Exchange[exchangesList.size()]);
	}

	public int getNumberOfExchanges(RuleKey ruleKey) {
		if (ruleKey == null || !ruleExchangeMap.containsKey(ruleKey)) {
			return 0;
		}

		return ruleExchangeMap.get(ruleKey).size();
	}

	public int getThreashold() {
		return threashold;
	}

	public void setThreashold(int threashold) {
		this.threashold = threashold;
	}

	public RuleStatistics getStatistics(RuleKey key) {
		RuleStatistics statistics = new RuleStatistics();
		statistics.setCountTotal(getNumberOfExchanges(key));
		List<Exchange> exchangesList = ruleExchangeMap.get(key);
		if (exchangesList == null || exchangesList.size() == 0)
			return statistics;

		int min = -1;
		int max = -1;
		long sum = 0;
		int count = 0;
		int errorCount = 0;
		long bytesSent = 0;
		long bytesReceived = 0;
		
		
		for (int i = 0; i < exchangesList.size(); i++) {
			if (exchangesList.get(i).getStatus() != ExchangeState.COMPLETED) {
				if (exchangesList.get(i).getStatus() == ExchangeState.FAILED)
					errorCount++;
				continue;
			}
			count++;
			int diff = (int) (exchangesList.get(i).getTimeResSent() - exchangesList.get(i).getTimeReqSent());
			sum += diff;
			if (min < 0 || diff < min) {
				min = diff;
			}

			if (diff > max) {
				max = diff;
			}
			
			bytesSent += exchangesList.get(i).getRequest().getBody().getLength();
			bytesReceived += exchangesList.get(i).getResponse().getBody().getLength();
		}

		if (count == 0)
			count++;

		statistics.setMin(min);
		statistics.setMax(max);
		statistics.setAvg(sum / count);
		statistics.setBytesSent(bytesSent);
		statistics.setBytesReceived(bytesReceived);
		return statistics;
	}

	public Object[] getAllExchanges() {
		if (totalList.size() == 0) 
			return null;
		
		return totalList.toArray();
	}

	public Object[] getLatExchanges(int count) {
		if (totalList.size() == 0 || count <= 0) 
			return null;
		
		if (count > totalList.size())
			return  totalList.toArray();
		
		List<Exchange> last = totalList.subList(count, totalList.size() - 1);
		if (last == null) 
			return null;
		
		return last.toArray();
	}

}
