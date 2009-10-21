package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.celleditors.RuleTreeCellEditorModifier;

public class RuleRenameAction extends Action {

	private TreeViewer treeView;
	
	public RuleRenameAction(TreeViewer treeView) {
		super();
		this.treeView = treeView;
		setText("Rename");
		setId("Rule Rename Action");
	}
	
	public void run() {
		IStructuredSelection selection = (IStructuredSelection) treeView.getSelection();
		Object selectedItem = selection.getFirstElement();
		
		if (selectedItem instanceof Rule) {
			RuleTreeCellEditorModifier modifier = (RuleTreeCellEditorModifier) treeView.getCellModifier();
			modifier.allowModify(true);
			treeView.editElement(selection.getFirstElement(), 0);
			modifier.allowModify(false); 
		}
	}
	
}
