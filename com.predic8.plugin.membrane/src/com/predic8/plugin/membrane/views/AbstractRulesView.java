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

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.model.IExchangesViewListener;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.actions.exchanges.RemoveAllExchangesAction;
import com.predic8.plugin.membrane.actions.rules.RemoveRuleAction;
import com.predic8.plugin.membrane.actions.rules.RenameRuleAction;
import com.predic8.plugin.membrane.actions.rules.RuleEditAction;
import com.predic8.plugin.membrane.actions.views.ShowRuleDetailsViewAction;
import com.predic8.plugin.membrane.celleditors.RuleNameCellEditorModifier;

public abstract class AbstractRulesView extends ViewPart implements IExchangesViewListener, IRuleChangeListener {

	
	protected TableViewer tableViewer;

	protected RuleNameCellEditorModifier cellEditorModifier;
	
	
	protected RuleEditAction editRuleAction;
	
	protected RemoveRuleAction removeRuleAction;
	
	protected RemoveAllExchangesAction removeAllExchangesAction;
	
	protected RenameRuleAction renameRuleAction;
	
	protected ShowRuleDetailsViewAction showRuleDetailsAction;
	
	protected void createActions () {
		removeRuleAction = new RemoveRuleAction(tableViewer);
		removeRuleAction.setEnabled(false);
		
		editRuleAction = new RuleEditAction(tableViewer);
		editRuleAction.setEnabled(false);
		
		removeAllExchangesAction = new RemoveAllExchangesAction(tableViewer);
		removeAllExchangesAction.setEnabled(false);
		
		renameRuleAction = new RenameRuleAction(tableViewer);
		renameRuleAction.setEnabled(false);
		
		
		showRuleDetailsAction = new ShowRuleDetailsViewAction(tableViewer);
		showRuleDetailsAction.setEnabled(false);
	}
	
	protected void addTableMenu() {
		MenuManager menuManager = new MenuManager();
		menuManager.add(removeRuleAction);
		menuManager.add(editRuleAction);
		menuManager.add(removeAllExchangesAction);
		menuManager.add(renameRuleAction);
		menuManager.add(showRuleDetailsAction);
		
		final Menu menu = menuManager.createContextMenu(tableViewer.getControl());
		tableViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuManager, tableViewer);
	}
	
	@Override
	public void setFocus() {
		tableViewer.getTable().setFocus();
	}
	
	public void setInputForTable(RuleManager manager) {
		if (manager.getTotalNumberOfRules() > 0) {
			enableActions(true);
		} else {
			enableActions(false);
		}
		if ( tableViewer.getContentProvider() != null)
				tableViewer.setInput(manager);
	}

	public void addExchange(Rule rule, Exchange exchange) {
		
	}

	public void ruleAdded(Rule rule) {
		enableActions(true);
		refreshTable();
	}

	public void refresh() {
		refreshTable();
	}

	public void removeExchange(Exchange exchange) {
		refreshTable();
	}

	public void removeExchanges(Rule parent, Exchange[] exchanges) {
		refreshTable();
	}

	public void removeRule(Rule rule, int rulesLeft) {
		if (rulesLeft == 0){
			enableActions(false);
		}
		refreshTable();
	}

	public void setExchangeFinished(Exchange exchange) {
		refreshTable();
	}
	
	private void enableActions(boolean enabled) {
		editRuleAction.setEnabled(enabled);
		removeRuleAction.setEnabled(enabled);
		removeAllExchangesAction.setEnabled(enabled);
		renameRuleAction.setEnabled(enabled);
		showRuleDetailsAction.setEnabled(enabled);
	}
	
	private void refreshTable() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				if (tableViewer.getTable() == null || tableViewer.getTable().isDisposed() || tableViewer.getContentProvider() == null)
					return;
				tableViewer.setInput(Router.getInstance().getRuleManager());
			}
		});

	}

	public void removeExchanges(Exchange[] exchanges) {
		refreshTable();
	}
	
}
