package com.predic8.plugin.membrane.views;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.actions.RemoveAllExchangesAction;
import com.predic8.plugin.membrane.actions.RemoveRuleAction;
import com.predic8.plugin.membrane.actions.RenameRuleAction;
import com.predic8.plugin.membrane.actions.RuleEditAction;
import com.predic8.plugin.membrane.actions.ShowRuleDetailsViewAction;
import com.predic8.plugin.membrane.celleditors.RuleNameCellEditorModifier;

public abstract class AbstractRulesView extends ViewPart implements IRuleTreeViewerListener {

	
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
		tableViewer.setInput(manager);
	}

	public void addExchange(Rule rule, Exchange exchange) {
		
	}

	public void addRule(Rule rule) {
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

	public void selectTo(Object obj) {
		
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
		if (tableViewer.getTable() == null || tableViewer.getTable().isDisposed())
			return;
		tableViewer.getTable().getDisplay().asyncExec(new Runnable() {
			public void run() {
				tableViewer.setInput(Router.getInstance().getRuleManager());
			}
		});
	}

	public void removeExchanges(Exchange[] exchanges) {
		refreshTable();
	}
	
}
