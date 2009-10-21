package com.predic8.plugin.membrane.celleditors;

import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.TreeItem;

import com.predic8.membrane.core.rules.Rule;

public class RuleTreeCellEditorModifier implements ICellModifier {

	private boolean allowModify;
	
	private TreeViewer treeViewer;
	
	public boolean canModify(Object element, String property) {
		if(element != null && element instanceof Rule && "name".equals(property) && allowModify) {
			return true;
		}
		return false;
	}

	public Object getValue(Object element, String property) {
		return ((Rule) element).toString();
	}

	public void modify(Object element, String property, Object value) {
		if(element instanceof TreeItem && "name".equals(property)) {
			TreeItem item = (TreeItem) element;
			((Rule)item.getData()).setName(value.toString());
			treeViewer.update(item.getData(), null);
		} 
	}

	public boolean isAllowModify() {
		return allowModify;
	}

	public void allowModify(boolean canModify) {
		this.allowModify = canModify;
	}

	public TreeViewer getTreeViewer() {
		return treeViewer;
	}

	public void setTreeViewer(TreeViewer treeViewer) {
		this.treeViewer = treeViewer;
	}
	
}
