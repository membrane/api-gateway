package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import com.predic8.membrane.core.Core;
import com.predic8.membrane.core.rules.Rule;

public class RemoveAllExchangesAction extends Action {

	private TreeViewer treeViewer;
	
	public RemoveAllExchangesAction(TreeViewer treeViewer) {
		super();
		this.treeViewer = treeViewer;
		setText("Remove all exchanges");
		setId("remove all exhanges action");
	}
	
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
		Object selectedItem = selection.getFirstElement();
		if (selectedItem instanceof Rule) {
			Core.getExchangeStore().removeAllExchanges((Rule) selectedItem);
		}
	}
	
}
