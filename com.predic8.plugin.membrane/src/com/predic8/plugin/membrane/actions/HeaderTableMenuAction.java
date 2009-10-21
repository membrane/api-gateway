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

package com.predic8.plugin.membrane.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;

import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.plugin.membrane.dialogs.AddHeaderFieldDialog;

public class HeaderTableMenuAction {
	
	private TableViewer tableViewer;

	private RemoveAction removeAction;

	private RefreshAction refreshAction;
	
	private AddHeaderFieldAction addHeaderFieldAction;
	
	private EditHeaderFieldAction editHeaderFieldAction;
	
	private MenuManager mgr;

	private AddHeaderFieldDialog addHeaderFieldDialog;
	
	public HeaderTableMenuAction(TableViewer tableView) {
		this.tableViewer = tableView;
		removeAction = new RemoveAction(tableViewer);
		refreshAction = new RefreshAction(tableViewer);
		addHeaderFieldAction = new AddHeaderFieldAction(tableViewer);
		editHeaderFieldAction = new EditHeaderFieldAction(tableViewer);
		
		mgr = new MenuManager();
		Menu menu = mgr.createContextMenu(tableView.getTable());
		tableView.getTable().setMenu(menu);
		mgr.add(removeAction);
		mgr.add(refreshAction);
		mgr.add(editHeaderFieldAction);
		mgr.add(addHeaderFieldAction);
	}

	public void setEnableRemoveAction(boolean bool) {
		removeAction.setEnabled(bool);
	}

	class RemoveAction extends Action {
		private TableViewer parentTableView;

		public RemoveAction(TableViewer tableView) {
			this.parentTableView = tableView;

			setText("Remove");
		}

		public void run() {
			IStructuredSelection selection = (IStructuredSelection) parentTableView.getSelection();
			Object selectedItem = selection.getFirstElement();

			if (selectedItem instanceof HeaderField) {
				HeaderField headerField = (HeaderField) selectedItem;
				Message message = (Message) parentTableView.getInput();
				message.getHeader().remove(headerField);
				parentTableView.remove(headerField);
			}

		}
	}

	class RefreshAction extends Action {
		private TableViewer parentTableView;

		public RefreshAction(TableViewer tableView) {
			this.parentTableView = tableView;
			setText("Refresh");

		}

		public void run() {
			parentTableView.refresh();
		}
	}

	class AddHeaderFieldAction extends Action {
		private TableViewer parentTableViewer;

		public AddHeaderFieldAction(TableViewer tableView) {
			this.parentTableViewer = tableView;
			setText("Add");

		}

		public void run() {
			if (addHeaderFieldDialog == null) {
				addHeaderFieldDialog = new AddHeaderFieldDialog(Display.getCurrent().getActiveShell(), parentTableViewer);
			}
			addHeaderFieldDialog.open();
		}
	}
	
	
	class EditHeaderFieldAction extends Action {
		private TableViewer parentTableViewer;

		public EditHeaderFieldAction(TableViewer tableView) {
			this.parentTableViewer = tableView;
			setText("Edit");
		}

		public void run() {
			IStructuredSelection selection = (IStructuredSelection) parentTableViewer.getSelection();
			Object selectedItem = selection.getFirstElement();
			if (selectedItem instanceof HeaderField) {
				parentTableViewer.editElement(selectedItem, 1);
			} 
		}
	}
	
}