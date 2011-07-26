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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.celleditors.RuleNameCellEditorModifier;
import com.predic8.plugin.membrane.contentproviders.RuleStatisticsContentProvider;
import com.predic8.plugin.membrane.labelproviders.RuleStatisticsLabelProvider;
import com.predic8.plugin.membrane.labelproviders.TableHeaderLabelProvider;

public class RuleStatisticsView extends AbstractRulesView {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleStatisticsView";

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = createComposite(parent);

		createRefreshButton(composite);
		
		tableViewer = createTableViewer(composite);
	
		addCellEditorsAndModifiersToViewer();
		
		tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				Object selectedItem = selection.getFirstElement();
				if (selectedItem instanceof Rule) {
					tableViewer.editElement(selectedItem, 0);
				} 
			}
		});
		
		new Label(composite, SWT.NONE).setText(" All times in ms");
		
		
		createActions();
		
		// fix for 2.0.X, do not merge into 2.1.X 
		// addTableMenu();
				
	    getExchangeStore().addExchangesViewListener(this);
	    setInputForTable(Router.getInstance().getRuleManager());
	}


	private void createRefreshButton(Composite composite) {
		Button btRefresh = new Button(composite, SWT.PUSH);
		btRefresh.setText("Refresh Table");
		btRefresh.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				 setInputForTable(Router.getInstance().getRuleManager());
			}
		});
	}


	private ExchangeStore getExchangeStore() {
		return Router.getInstance().getExchangeStore();
	}


	private void addCellEditorsAndModifiersToViewer() {
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
	}


	private TableViewer createTableViewer(Composite composite) {
		TableViewer viewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(viewer);
		viewer.setContentProvider(new RuleStatisticsContentProvider());
		viewer.setLabelProvider(new RuleStatisticsLabelProvider());
		
		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.grabExcessVerticalSpace = true;
		gridData.grabExcessHorizontalSpace = true;
		viewer.getTable().setLayoutData(gridData);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MembraneUIPlugin.PLUGIN_ID + "RuleStatistics");
		return viewer;
	}


	private Composite createComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 5;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 5;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);
		return composite;
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
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
	}


	public void ruleRemoved(Rule rule) {
		setInputForTable(Router.getInstance().getRuleManager());
	}


	public void ruleUpdated(Rule rule) {
		setInputForTable(Router.getInstance().getRuleManager());
	}


	public void rulePositionsChanged() {
		setInputForTable(Router.getInstance().getRuleManager());
	}


	public void setExchangeStopped(AbstractExchange exchange) {
		// TODO Auto-generated method stub
		
	}


}
