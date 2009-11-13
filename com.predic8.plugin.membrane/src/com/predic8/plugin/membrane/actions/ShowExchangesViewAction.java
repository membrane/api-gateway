package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import com.predic8.plugin.membrane.views.ExchangesView;

public class ShowExchangesViewAction implements IWorkbenchWindowActionDelegate {

	public static final String ACTION_ID = "com.predic8.plugin.membrane.actions.ShowExchangesViewAction";
	
	private IWorkbenchWindow window;
	
	public void dispose() {
		
	}

	public void init(IWorkbenchWindow window) {
		this.window = window;
	}

	public void run(IAction action) {
		IWorkbenchPage page = window.getActivePage();
		try {
			page.showView(ExchangesView.VIEW_ID);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void selectionChanged(IAction action, ISelection selection) {

	}

}
