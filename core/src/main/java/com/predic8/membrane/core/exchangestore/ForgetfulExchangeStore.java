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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;

@MCElement(name="forgetfulExchangeStore")
public class ForgetfulExchangeStore implements ExchangeStore {

	public void snap(AbstractExchange exchange, Flow flow) {
	}

	public void addExchangesStoreListener(IExchangesStoreListener viewer) {
		
	}

	public AbstractExchange[] getExchanges(RuleKey ruleKey) {
		return null;
	}

	public int getNumberOfExchanges(RuleKey ruleKey) {
		return 0;
	}

	public StatisticCollector getStatistics(RuleKey ruleKey) {
		return null;
	}

	
	public void notifyListenersOnExchangeAdd(Rule rule, AbstractExchange exchange) {
		
	}

	
	public void notifyListenersOnExchangeRemoval(AbstractExchange exchange) {
		
	}

	
	public void notifyListenersOnRuleAdd(Rule rule) {
		
	}

	
	public void notifyListenersOnRuleRemoval(Rule rule, int rulesLeft) {
		
	}

	
	public void refreshExchangeStoreListeners() {
		
	}

	
	public void remove(AbstractExchange exchange) {
		
	}
	
	public void removeAllExchanges(Rule rule) {
		
	}

	
	public void removeExchangesStoreListener(IExchangesStoreListener viewer) {
		
	}

	public Object[] getAllExchanges() {
		return null;
	}

	public Object[] getLatExchanges(int count) {
		
		return null;
	}

	public List<AbstractExchange> getAllExchangesAsList() {
		return null;
	}

	public void removeAllExchanges(AbstractExchange[] exchanges) {
		
	}

	public AbstractExchange getExchangeById(int intParam) {
		return null;
	}

	@Override
	public void init() throws Exception {
	}

	public List<? extends ClientStatistics> getClientStatistics() {
		return null;
	}
	
	public void collect(ExchangeCollector collector) {}
	
}
