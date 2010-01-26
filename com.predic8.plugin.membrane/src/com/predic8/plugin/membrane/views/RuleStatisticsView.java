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

package com.predic8.plugin.membrane.views;


import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.celleditors.RuleNameCellEditorModifier;
import com.predic8.plugin.membrane.contentproviders.RuleStatisticsContentProvider;
import com.predic8.plugin.membrane.labelproviders.RuleStatisticsLabelProvider;
import com.predic8.plugin.membrane.labelproviders.TableHeaderLabelProvider;

public class RuleStatisticsView extends AbstractRulesView {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleStatisticsView";

	public RuleStatisticsView() {
		
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 5;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 5;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);

		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		tableViewer.setContentProvider(new RuleStatisticsContentProvider());
		tableViewer.setLabelProvider(new RuleStatisticsLabelProvider());
		
		GridData tableGridData = new GridData(GridData.FILL_BOTH);
		tableGridData.grabExcessVerticalSpace = true;
		tableGridData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(tableGridData);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MembraneUIPlugin.PLUGIN_ID + "RuleStatistics");
	
		
		final CellEditor[] cellEditors = new CellEditor[1];
		cellEditors[0] = new TextCellEditor(tableViewer.getTable(), SWT.BORDER);
		tableViewer.setCellEditors(cellEditors);
		tableViewer.setColumnProperties(new String[] {"name"});
		
		
		cellEditorModifier = new RuleNameCellEditorModifier();
		cellEditorModifier.setTableViewer(tableViewer);
		tableViewer.setCellModifier(cellEditorModifier);
		
		TableViewerEditor.create(tableViewer, new ColumnViewerEditorActivationStrategy(tableViewer) {
			protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
				return event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC;
			}
		}, ColumnViewerEditor.DEFAULT);
		
		tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				Object selectedItem = selection.getFirstElement();
				if (selectedItem instanceof Rule) {
					tableViewer.editElement(selectedItem, 0);
				} 
			}
		});
		
		Label lbTimeUnit = new Label(composite, SWT.NONE);
		lbTimeUnit.setText(" All times in ms");
		
		createActions();
		addTableMenu();
				
	    Router.getInstance().getExchangeStore().addExchangesViewListener(this);
	    setInputForTable(Router.getInstance().getRuleManager());
	}

	
	private void createColumns(TableViewer viewer) {
		String[] titles = { "Rule", "Exchanges", "Minimum Time", "Maximum Time", "Average Time", "Bytes Sent", "Bytes Received", "Errors"};
		int[] bounds = { 140, 80, 90, 90, 100, 80, 90, 70};	
		
		for (int i = 0; i < titles.length; i++) {
			TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
			column.getViewer().setLabelProvider(new TableHeaderLabelProvider());
			column.getColumn().setAlignment(SWT.CENTER);
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);
		}
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
	}
}
