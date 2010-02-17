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
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.celleditors.RuleNameCellEditorModifier;
import com.predic8.plugin.membrane.components.composites.RulesViewControlsComposite;
import com.predic8.plugin.membrane.contentproviders.RulesViewContentProvider;
import com.predic8.plugin.membrane.labelproviders.RulesViewLabelProvider;
import com.predic8.plugin.membrane.labelproviders.TableHeaderLabelProvider;

public class RulesView extends AbstractRulesView {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RulesView";

	private RulesViewControlsComposite controlsComposite;
	
	@Override
	public void createPartControl(Composite parent) {
		Composite composite = createComposite(parent);

		tableViewer = createTableViewer(composite);	
		
		controlsComposite = new RulesViewControlsComposite(composite);
		
		
		createCommentLabel(composite);
		
		createActions();
		addTableMenu();
		
		Router.getInstance().getExchangeStore().addExchangesViewListener(this);
		Router.getInstance().getRuleManager().addRuleChangeListener(this);
		setInputForTable(Router.getInstance().getRuleManager());
	}

	private Composite createComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginTop = 10;
		layout.marginLeft = 5;
		layout.marginBottom = 20;
		layout.marginRight = 5;
		layout.verticalSpacing = 10;
		composite.setLayout(layout);
		return composite;
	}

	private void createCommentLabel(Composite composite) {
		Label label = new Label(composite, SWT.NONE);
		label.setText("Rules are evaluated in top-down direction.");
		GridData gData = new GridData();
		gData.horizontalSpan = 2;
		label.setLayoutData(gData);
	}

	private TableViewer createTableViewer(Composite composite) {
		final TableViewer tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		tableViewer.setContentProvider(new RulesViewContentProvider());
		
		
		tableViewer.setLabelProvider(new RulesViewLabelProvider());
		
		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.grabExcessVerticalSpace = true;
		gridData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(gridData);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MembraneUIPlugin.PLUGIN_ID + "RuleStatistics");

		setCellEditorForTableViewer(tableViewer);
		
		tableViewer.setColumnProperties(new String[] { "name" });

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
		
		tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
				if (selection == null || selection.isEmpty()) {
					controlsComposite.enableDependentButtons(false);
					return;
				}
				controlsComposite.enableDependentButtons(true);
				
				setSelectedRule((Rule)selection.getFirstElement());
			}

			private void setSelectedRule(Rule selectedRule) {
				removeRuleAction.setSelectedRule(selectedRule);
				editRuleAction.setSelectedRule(selectedRule);
				removeAllExchangesAction.setSelectedRule(selectedRule);
				showRuleDetailsAction.setSelectedRule(selectedRule);
				controlsComposite.setSelectedRule(selectedRule);
			}
		});	
		
		return tableViewer;  
	}

	private void setCellEditorForTableViewer(final TableViewer tableViewer) {
		final CellEditor[] cellEditors = new CellEditor[1];
		cellEditors[0] = new TextCellEditor(tableViewer.getTable(), SWT.BORDER);
		tableViewer.setCellEditors(cellEditors);
	}

	private void createColumns(TableViewer viewer) {
		String[] titles = { "Rule", "Exchanges"};
		int[] bounds = { 158, 80 };

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

}
