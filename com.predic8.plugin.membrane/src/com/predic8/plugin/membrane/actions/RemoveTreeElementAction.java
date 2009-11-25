package com.predic8.plugin.membrane.actions;

import java.io.IOException;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class RemoveTreeElementAction extends Action {

	private TreeViewer treeViewer;

	public RemoveTreeElementAction(TreeViewer treeViewer) {
		this.treeViewer = treeViewer;
		setText("Remove");
		setId("remove tree element action");
	}

	@Override
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
		Object selectedItem = selection.getFirstElement();
		if (selectedItem instanceof Exchange) {
			Exchange selectedExchange = (Exchange) selectedItem;
			selectedExchange.finishExchange(false);// Don't need to refresh.
			Router.getInstance().getExchangeStore().remove(selectedExchange);
			return;
		}

		if (selectedItem instanceof Rule) {
			Rule rule = (Rule) selectedItem;
			Router.getInstance().getRuleManager().removeRule(rule);
			
			treeViewer.setSelection(null);
			if (!Router.getInstance().getRuleManager().isAnyRuleWithPort(rule.getRuleKey().getPort())) {
				try {
					((HttpTransport) Router.getInstance().getTransport()).closePort(rule.getRuleKey().getPort());
				} catch (IOException e2) {
					e2.printStackTrace();
				}
			}
		} 
	}
	
}
