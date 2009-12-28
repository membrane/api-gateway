package com.predic8.plugin.membrane.celleditors;

import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.TableItem;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;

public class RuleNameCellEditorModifier implements ICellModifier {

	private boolean allowModify;
	
	private TableViewer tableViewer;
	
	public boolean canModify(Object element, String property) {
		if ("name".equals(property)) {
			return true;
		}
		return false;
	}

	public Object getValue(Object element, String property) {
		return ((Rule) element).toString();
	}

	public void modify(Object element, String property, Object value) {
		if(element instanceof TableItem && "name".equals(property)) {
			TableItem item = (TableItem) element;
			((Rule)item.getData()).setName(value.toString());
			Router.getInstance().getRuleManager().ruleChanged(((Rule)item.getData()));
			tableViewer.update(item.getData(), null);
		} 
	}

	public boolean isAllowModify() {
		return allowModify;
	}

	public void allowModify(boolean canModify) {
		this.allowModify = canModify;
	}

	public TableViewer getTableViewer() {
		return tableViewer;
	}

	public void setTableViewer(TableViewer viewer) {
		this.tableViewer = viewer;
	}
	
}
