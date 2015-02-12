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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.rules.Rule;

public abstract class AbstractExchangeStore implements ExchangeStore {

	protected Set<IExchangesStoreListener> exchangesStoreListeners = new HashSet<IExchangesStoreListener>();
	
	public void addExchangesStoreListener(IExchangesStoreListener viewer) {
		exchangesStoreListeners.add(viewer);
		
	}
	public void removeExchangesStoreListener(IExchangesStoreListener viewer) {
		exchangesStoreListeners.remove(viewer);
	}
	
	public void refreshExchangeStoreListeners(){
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.refresh();
		}
	}
	
	public void notifyListenersOnExchangeAdd(Rule rule, AbstractExchange exchange) {
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			exchange.addExchangeStoreListener(listener);
			listener.addExchange(rule, exchange);
		}
	}
	
	public void notifyListenersOnExchangeRemoval(AbstractExchange exchange) {
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			exchange.removeExchangeStoreListener(listener);
			listener.removeExchange(exchange);
		}
	}
	
	public AbstractExchange getExchangeById(int id) {
		throw new UnsupportedOperationException("getExchangeById must be implemented in the sub class.");
	}
		
	
	public List<? extends ClientStatistics> getClientStatistics() {
		throw new UnsupportedOperationException("getClientStatistics must be implemented in the sub class.");
	}
	
	public void init() {
	}

	public synchronized void collect(ExchangeCollector collector) {
		for (AbstractExchange exc: getAllExchangesAsList()) {
			collector.collect(exc);
		}	
	}
	
	
	
}
