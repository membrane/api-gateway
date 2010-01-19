package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.plugin.membrane.dialogs.rule.ForwardingRuleEditDialog;
import com.predic8.plugin.membrane.dialogs.rule.ProxyRuleEditDialog;

public class RuleEditAction extends Action {

	private StructuredViewer structuredViewer;

	public RuleEditAction(StructuredViewer viewer) {
		super();
		this.structuredViewer = viewer;
		setText("Edit Rule");
		setId("Rule Edit Action");
	}

	@Override
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) structuredViewer.getSelection();
		Object selectedItem = selection.getFirstElement();

		try {
			if (selectedItem instanceof ForwardingRule) {
				ForwardingRule selectedRule = (ForwardingRule) selectedItem;
				ForwardingRuleEditDialog dialog = new ForwardingRuleEditDialog(structuredViewer.getControl().getShell());
				if (dialog.getShell() == null) {
					dialog.create();
				}
				dialog.setInput(selectedRule);
				dialog.open();

				// EditForwardingRuleDialog dialog = new
				// EditForwardingRuleDialog(structuredViewer.getControl().getShell());
				// if(dialog.getShell()==null) {
				// dialog.create();
				// }
				// dialog.resetValueForRuleOptionsViewer(selectedRule);
				// dialog.open();
			} else if (selectedItem instanceof ProxyRule) {
				ProxyRule selectedRule = (ProxyRule) selectedItem;
				ProxyRuleEditDialog dialog = new ProxyRuleEditDialog(structuredViewer.getControl().getShell());
				if (dialog.getShell() == null) {
					dialog.create();
				}
				dialog.setInput(selectedRule);
				dialog.open();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

}
