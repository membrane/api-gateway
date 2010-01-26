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
