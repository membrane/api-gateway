package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.plugin.membrane.dialogs.EditForwardingRuleDialog;
import com.predic8.plugin.membrane.dialogs.EditProxyRuleDialog;

public class RuleEditAction extends Action {

	private TreeViewer treeView;
	
	public RuleEditAction(TreeViewer treeView) {
		super();
		this.treeView = treeView;
		setText("Edit Rule");
		setId("Rule Edit Action");
	}
	
	@Override
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) treeView.getSelection();
		Object selectedItem = selection.getFirstElement();
		
		try {
			if (selectedItem instanceof ForwardingRule) {
				ForwardingRule selectedRule = (ForwardingRule)selectedItem;
				EditForwardingRuleDialog dialog = new EditForwardingRuleDialog(treeView.getControl().getShell());
				if(dialog.getShell()==null) {
					dialog.create();
				}
				dialog.resetValueForRuleOptionsViewer(selectedRule);
				dialog.open();
			} else if (selectedItem instanceof ProxyRule) {
				ProxyRule selectedRule = (ProxyRule)selectedItem;
				EditProxyRuleDialog dialog = new EditProxyRuleDialog(treeView.getControl().getShell());
				if(dialog.getShell()==null) {
					dialog.create();
				}
				dialog.resetValueForRuleOptionsViewer(selectedRule);
				dialog.open();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		
	}
	
}
