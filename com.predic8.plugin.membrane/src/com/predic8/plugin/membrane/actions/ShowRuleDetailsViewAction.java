package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.views.RuleDetailsView;

public class ShowRuleDetailsViewAction extends Action {

	private StructuredViewer structuredViewer;

	public ShowRuleDetailsViewAction(StructuredViewer viewer) {
		this.structuredViewer = viewer;
		setText("Show Rule Details");
		setId("Show Rule Details Action");
	}

	public void run() {

		IStructuredSelection selection = (IStructuredSelection) structuredViewer.getSelection();
		Object selectedItem = selection.getFirstElement();

		if (selectedItem instanceof Rule) {
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				page.showView(RuleDetailsView.VIEW_ID);
				RuleDetailsView ruleView =  (RuleDetailsView)page.findView(RuleDetailsView.VIEW_ID);
				ruleView.setRuleToDisplay((Rule) selectedItem);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}

}
