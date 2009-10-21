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

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;

public abstract class AbstractExchangeStore implements ExchangeStore {

	protected Set<IRuleTreeViewerListener> treeViewerListeners = new HashSet<IRuleTreeViewerListener>();
	
	public void addTreeViewerListener(IRuleTreeViewerListener viewer) {
		treeViewerListeners.add(viewer);
		
	}
	public void removeTreeViewerListener(IRuleTreeViewerListener viewer) {
		treeViewerListeners.remove(viewer);
	}
	
	public void refreshAllTreeViewers(){
		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			listener.refresh();
		}
	}
	
	public void notifyListenersOnExchangeAdd(Rule rule, Exchange exchange) {
		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			exchange.addTreeViewerListener(listener);
			listener.addExchange(rule, exchange);
		}
	}
	
	public void notifyListenersOnExchangeRemoval(Exchange exchange) {
		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			exchange.removeTreeViewerListener(listener);
			listener.removeExchange(exchange);
		}
	}
	
	public void notifyListenersOnRuleAdd(Rule rule) {
		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			listener.addRule(rule);
		}
	}
	
	public void notifyListenersOnRuleRemoval(Rule rule, int rulesLeft) {
		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			listener.removeRule(rule, rulesLeft);
		}
	}
	
}
