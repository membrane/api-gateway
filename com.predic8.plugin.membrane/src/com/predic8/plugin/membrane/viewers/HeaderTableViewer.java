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

package com.predic8.plugin.membrane.viewers;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import com.predic8.membrane.core.http.HeaderField;
import com.predic8.plugin.membrane.actions.HeaderTableMenuAction;
import com.predic8.plugin.membrane.celleditors.HeaderTableCellModifier;
import com.predic8.plugin.membrane.contentproviders.HeaderTableContentProvider;
import com.predic8.plugin.membrane.labelproviders.HeaderTableLabelProvider;

public class HeaderTableViewer extends TableViewer {

	private Table table;

	private String[] columnNames = new String[] { "Header Names", "Value" };

	private HeaderTableMenuAction menuAction;

	private CellEditor[] editors;

	public HeaderTableViewer(Composite parent, int style) {
		super(parent, style);

		table = getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		TableLayout layout = new TableLayout();
		table.setLayout(layout);

		layout.addColumnData((new ColumnWeightData(1)));

		new TableColumn(table, SWT.NONE).setText("Header Name");

		layout.addColumnData((new ColumnWeightData(2)));

		new TableColumn(table, SWT.NONE).setText("Value");

		setContentProvider(new HeaderTableContentProvider());

		setLabelProvider(new HeaderTableLabelProvider());

		setColumnProperties(columnNames);

		editors = new CellEditor[] { new TextCellEditor(table), new TextCellEditor(table) };

		setCellEditors(editors);
		setCellModifier(new HeaderTableCellModifier(this));
		TableViewerEditor.create(this, new ColumnViewerEditorActivationStrategy(this) {
			protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
				return event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC;
			}
		}, ColumnViewerEditor.DEFAULT);
		menuAction = new HeaderTableMenuAction(this);

		addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {

				if (!(e.getSelection() instanceof IStructuredSelection))
					return;

				menuAction.setEnableRemoveAction(isHeaderField(e));				
			}
			
			private boolean isHeaderField(SelectionChangedEvent e) {
				return ((IStructuredSelection) e.getSelection()).getFirstElement() instanceof HeaderField;
			}
		});

		addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				Object selectedItem = ((IStructuredSelection) event.getSelection()).getFirstElement();
				if (selectedItem instanceof HeaderField) {
					HeaderTableViewer.this.editElement(selectedItem, 1);
				}
			}
		});
	}

	public void setEditable(boolean bool) {
		if (bool) {
			setCellEditors(editors);
		} else {
			cancelEditing();
			setCellEditors(null);
		}
	}

}
