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

package com.predic8.plugin.membrane.views;

import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;

public abstract class AbstractProxiesView extends TableViewPart implements IExchangesStoreListener, IRuleChangeListener {

	public void setInputForTable(RuleManager manager) {
		tableViewer.setInput(manager);
	}

	public void addExchange(Rule rule, AbstractExchange exchange) {
		
	}

	@Override
	public void ruleAdded(Rule rule) {
		refreshTable();
	}

	@Override
	public void batchUpdate(int size) {
		refreshTable();
	}
	
	public void refresh() {
		refreshTable();
	}

	public void removeExchange(AbstractExchange exchange) {
		refreshTable();
	}

	public void removeExchanges(Rule parent, AbstractExchange[] exchanges) {
		refreshTable();
	}

//	public void removeRule(Rule rule, int rulesLeft) {
//		refreshTable();
//	}

	public void setExchangeFinished(AbstractExchange exchange) {
		refreshTable();
	}
	
	private void refreshTable() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				if (tableViewer.getTable() == null || tableViewer.getTable().isDisposed())
					return;
				
				tableViewer.setInput(Router.getInstance().getRuleManager());
			}
		});

	}

	public void removeExchanges(AbstractExchange[] exchanges) {
		refreshTable();
	}
	
}
