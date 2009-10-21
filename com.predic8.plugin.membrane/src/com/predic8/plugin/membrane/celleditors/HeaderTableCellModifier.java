package com.predic8.plugin.membrane.celleditors;

import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.swt.widgets.TableItem;

import com.predic8.membrane.core.http.HeaderField;
import com.predic8.plugin.membrane.viewers.HeaderTableViewer;

public class HeaderTableCellModifier implements ICellModifier {

	private HeaderTableViewer tableViewer;
	
	public HeaderTableCellModifier(HeaderTableViewer viewer) {
		this.tableViewer = viewer;
	}
	
	
	public boolean canModify(Object element, String property) {
		if ("Value".equals(property)) {
			return true;
		}
		return false;
	}

	public Object getValue(Object element, String property) {
		if (element instanceof HeaderField && "Value".equals(property)) {
			return ((HeaderField)element).getValue();
		} 
		return null;
	}

	public void modify(Object element, String property, Object value) {
		if (element instanceof TableItem && "Value".equals(property)) {
			TableItem item = (TableItem)element;
			HeaderField headerField = (HeaderField)item.getData();
			headerField.setValue((String)value);
			tableViewer.refresh();
		}
	}

}
