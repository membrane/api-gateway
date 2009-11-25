package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.views.RuleTableView;

public class ShowRulesTableAction implements IWorkbenchWindowActionDelegate {

	private IWorkbenchWindow window;
	
	public ShowRulesTableAction() {
		
	}
	
	public void dispose() {

	}

	public void init(IWorkbenchWindow window) {
		this.window = window;
	}

	public void run(IAction action) {
		IWorkbenchPage page = window.getActivePage();
		try {
			page.showView(RuleTableView.VIEW_ID);
			RuleTableView ruleTableView = (RuleTableView)window.getActivePage().findView(RuleTableView.VIEW_ID);
			ruleTableView.getTableViewer().setInput(Router.getInstance().getRuleManager());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void selectionChanged(IAction action, ISelection selection) {
		
	}

	
}
