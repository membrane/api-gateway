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
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.celleditors.RuleNameCellEditorModifier;
import com.predic8.plugin.membrane.components.composites.RulesViewControlsComposite;
import com.predic8.plugin.membrane.contentproviders.RulesViewContentProvider;
import com.predic8.plugin.membrane.labelproviders.RulesViewLabelProvider;

public class RulesView extends AbstractRulesView {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RulesView";

	private RulesViewControlsComposite controlsComposite;
	
	@Override
	public void createPartControl(Composite parent) {
		Composite composite = createComposite(parent);

		createTableViewer(composite);
		extendTableViewer();
		
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

	@Override
	protected IBaseLabelProvider createLabelProvider() {
		return new RulesViewLabelProvider();
	}
	
	@Override
	protected IContentProvider createContentProvider() {
		return new RulesViewContentProvider();
	}
	
	private void extendTableViewer() {
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
			
				updatedetailsViewIfVisible(selectedRule);
			}

		});	
		
	}

	private void updatedetailsViewIfVisible(Rule selectedRule) {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IViewPart part = page.findView(RuleDetailsView.VIEW_ID);
		if (part == null || !page.isPartVisible(part)) 
			return;
		
		RuleDetailsView ruleView = (RuleDetailsView)part;
		ruleView.setRuleToDisplay(selectedRule);
		
	}
	
	private void setCellEditorForTableViewer(final TableViewer tableViewer) {
		final CellEditor[] cellEditors = new CellEditor[1];
		cellEditors[0] = new TextCellEditor(tableViewer.getTable(), SWT.BORDER);
		tableViewer.setCellEditors(cellEditors);
	}

	@Override
	protected String[] getTableColumnTitles() {
		return new String[] { "Rule", "Exchanges"};
	}
	
	@Override
	protected int[] getTableColumnBounds() {
		return new int[] { 158, 80 };
	}
	
	public void ruleRemoved(Rule rule) {
		setInputForTable(Router.getInstance().getRuleManager());
		changeSelectionAfterRemoval();
	}

	public void ruleUpdated(Rule rule) {
		setInputForTable(Router.getInstance().getRuleManager());
	}

	public void rulePositionsChanged() {
		setInputForTable(Router.getInstance().getRuleManager());
	}

	
	private void changeSelectionAfterRemoval() {
		if (tableViewer.getTable().getItemCount() == 0) {
			updatedetailsViewIfVisible(null);
			return;
		}
		TableItem item = tableViewer.getTable().getItem(0);
		tableViewer.setSelection(new StructuredSelection(item.getData()));
		notifytableSelectionListeners(item); 
	}

	private void notifytableSelectionListeners(TableItem item) {
		Event e = new Event();
		e.item = item;
		e.widget = tableViewer.getTable();
		e.type = SWT.Selection;
		tableViewer.getTable().notifyListeners(SWT.Selection, e);
	}

	public void setExchangeStopped(AbstractExchange exchange) {
		// TODO Auto-generated method stub
		
	}
	
}
