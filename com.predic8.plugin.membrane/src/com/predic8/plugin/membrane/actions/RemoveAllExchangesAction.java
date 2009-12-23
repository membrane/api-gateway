package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;

public class RemoveAllExchangesAction extends Action {

	private StructuredViewer viewer;
	
	public RemoveAllExchangesAction(StructuredViewer structuredViewer) {
		super();
		this.viewer = structuredViewer;
		setText("Remove all exchanges");
		setId("remove all exhanges action");
	}
	
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
		Object selectedItem = selection.getFirstElement();
		if (selectedItem instanceof Rule) {
			Router.getInstance().getExchangeStore().removeAllExchanges((Rule) selectedItem);
		}
	}
	
}
