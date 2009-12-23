package com.predic8.plugin.membrane.actions;

import java.io.IOException;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class RemoveRuleAction extends Action {

	private StructuredViewer structuredViewer;

	public RemoveRuleAction(StructuredViewer viewer) {
		this.structuredViewer = viewer;
		setText("Remove Rule");
		setId("Remove Rule Action");
	}

	@Override
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) structuredViewer.getSelection();
		Object selectedItem = selection.getFirstElement();
		
		if (selectedItem instanceof Rule) {
			Rule rule = (Rule) selectedItem;
			Router.getInstance().getRuleManager().removeRule(rule);
			structuredViewer.setSelection(null);
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
