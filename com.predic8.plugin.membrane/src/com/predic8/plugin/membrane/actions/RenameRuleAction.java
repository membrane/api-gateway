package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;

import com.predic8.membrane.core.rules.Rule;

public class RenameRuleAction extends Action {

	private TableViewer tableViewer;
	
	public RenameRuleAction(TableViewer treeView) {
		super();
		this.tableViewer = treeView;
		setText("Rename Rule");
		setId("Rename Rule Action");
	}
	
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
		Object selectedItem = selection.getFirstElement();
		
		if (selectedItem instanceof Rule) {
			tableViewer.editElement(selection.getFirstElement(), 0);
		}
	}
	
}
