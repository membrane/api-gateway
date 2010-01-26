/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
