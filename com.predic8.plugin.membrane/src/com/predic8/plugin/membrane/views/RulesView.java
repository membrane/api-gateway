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
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.celleditors.RuleNameCellEditorModifier;
import com.predic8.plugin.membrane.contentproviders.RulesViewContentProvider;
import com.predic8.plugin.membrane.labelproviders.RulesViewLabelProvider;
import com.predic8.plugin.membrane.labelproviders.TableHeaderLabelProvider;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.wizards.AddRuleWizard;

public class RulesView extends AbstractRulesView {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RulesView";

	private Button btEdit;
	
	private Button btRemove;
	
	private Button btUp, btDown;

	private Rule selectedRule;
	
	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginTop = 10;
		layout.marginLeft = 5;
		layout.marginBottom = 20;
		layout.marginRight = 5;
		layout.verticalSpacing = 20;
		composite.setLayout(layout);

		createTableViewer(composite);	
		
		Composite controlsComposite = createControlsComposite(composite);
		
		addControlsToComposite(controlsComposite);
		
		createActions();
		addTableMenu();
		
		Router.getInstance().getExchangeStore().addExchangesViewListener(this);
		setInputForTable(Router.getInstance().getRuleManager());
	}

	private void addControlsToComposite(Composite composite) {
		createAddButton(composite);
		btEdit = createEditButton(composite);
		btRemove = createRemoveButton(composite);
		btUp = createUpButton(composite);
		btDown = createDownButton(composite);
		new Label(composite, SWT.NONE).setText(" ");
		new Label(composite, SWT.NONE).setText(" ");
		new Label(composite, SWT.NONE).setText(" ");
		new Label(composite, SWT.NONE).setText(" ");
	}

	private Composite createControlsComposite(Composite parent) {
		Composite controls = new Composite(parent, SWT.NONE);
		RowLayout rowLayout = new RowLayout();
		rowLayout.type = SWT.VERTICAL;
		rowLayout.spacing = 15;
		rowLayout.fill = true;
		controls.setLayout(rowLayout);
		return controls;
	}
	
	private Button createAddButton(Composite composite) {
		Button bt = new Button(composite, SWT.PUSH);
		bt.setImage(MembraneUIPlugin.getDefault().getImageRegistry().get(ImageKeys.IMAGE_ADD_RULE));
		bt.setText("Add");
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				WizardDialog wizardDialog = new WizardDialog(Display.getCurrent().getActiveShell(), new AddRuleWizard());
				wizardDialog.create();
				wizardDialog.open();
			}

		});
		return bt;
	}

	private void createTableViewer(Composite composite) {
		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		tableViewer.setContentProvider(new RulesViewContentProvider());
		
		
		tableViewer.setLabelProvider(new RulesViewLabelProvider());
		

		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.grabExcessVerticalSpace = true;
		gridData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(gridData);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MembraneUIPlugin.PLUGIN_ID + "RuleStatistics");

		final CellEditor[] cellEditors = new CellEditor[1];
		cellEditors[0] = new TextCellEditor(tableViewer.getTable(), SWT.BORDER);
		tableViewer.setCellEditors(cellEditors);
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
					enableButtonsOnSelection(false);
					return;
				}
				enableButtonsOnSelection(true);
				selectedRule = (Rule)selection.getFirstElement();
			}

		});	
	}

	private void enableButtonsOnSelection(boolean status) {
		btEdit.setEnabled(status);
		btRemove.setEnabled(status);
		btUp.setEnabled(status);
		btDown.setEnabled(status);
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
	
	private Button createEditButton(final Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Edit");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				editRuleAction.run();
			}
		});
		return bt;
	}

	private Button createRemoveButton(Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Remove");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				removeRuleAction.run();
			}
		});
		return bt;
	}

	private Button createUpButton(Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Up");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Router.getInstance().getRuleManager().ruleUp(selectedRule);
				setInputForTable(Router.getInstance().getRuleManager());
			}
		});
		return bt;
	}

	private Button createDownButton(Composite controlsComposite) {
		Button btDown = new Button(controlsComposite, SWT.PUSH);
		btDown.setText("Down");
		btDown.setEnabled(false); 
		btDown.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Router.getInstance().getRuleManager().ruleDown(selectedRule);
				setInputForTable(Router.getInstance().getRuleManager());
			}
		});
		return btDown;
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
