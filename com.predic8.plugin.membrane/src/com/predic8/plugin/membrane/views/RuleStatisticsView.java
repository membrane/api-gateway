package com.predic8.plugin.membrane.views;


import org.eclipse.jface.action.MenuManager;
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
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.actions.RemoveAllExchangesAction;
import com.predic8.plugin.membrane.actions.RemoveRuleAction;
import com.predic8.plugin.membrane.actions.RenameRuleAction;
import com.predic8.plugin.membrane.actions.RuleEditAction;
import com.predic8.plugin.membrane.actions.ShowRuleDetailsViewAction;
import com.predic8.plugin.membrane.celleditors.StatisticsTableCellEditorModifier;
import com.predic8.plugin.membrane.providers.RuleStatisticsContentProvider;
import com.predic8.plugin.membrane.providers.RuleStatisticsLabelProvider;
import com.predic8.plugin.membrane.providers.TableHeaderLabelProvider;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.wizards.AddRuleWizard;

public class RuleStatisticsView extends ViewPart implements IRuleTreeViewerListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleStatisticsView";

	private TableViewer tableViewer;

	private StatisticsTableCellEditorModifier cellEditorModifier;
	
	
	private RuleEditAction editRuleAction;
	
	private RemoveRuleAction removeRuleAction;
	
	private RemoveAllExchangesAction removeAllExchangesAction;
	
	private RenameRuleAction renameRuleAction;
	
	private ShowRuleDetailsViewAction showRuleDetailsAction;
	
	public RuleStatisticsView() {
		
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		//scomposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
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
		
		
		cellEditorModifier = new StatisticsTableCellEditorModifier();
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
		
		removeRuleAction = new RemoveRuleAction(tableViewer);
		removeRuleAction.setEnabled(false);
		
		editRuleAction = new RuleEditAction(tableViewer);
		editRuleAction.setEnabled(false);
		
		removeAllExchangesAction = new RemoveAllExchangesAction(tableViewer);
		removeAllExchangesAction.setEnabled(false);
		
		renameRuleAction = new RenameRuleAction(tableViewer);
		renameRuleAction.setEnabled(false);
		
		
		showRuleDetailsAction = new ShowRuleDetailsViewAction(tableViewer);
		showRuleDetailsAction.setEnabled(false);
		
		addTableMenu();
		
		Button btAddRule = new Button(composite, SWT.PUSH);
		btAddRule.setImage(MembraneUIPlugin.getDefault().getImageRegistry().get(ImageKeys.IMAGE_ADD_RULE));
		btAddRule.setText("Add Rule");
		btAddRule.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				WizardDialog wizardDialog = new WizardDialog(Display.getCurrent().getActiveShell(), new AddRuleWizard());
				wizardDialog.create();
				wizardDialog.open();
			}
		
		});
		
	    Router.getInstance().getExchangeStore().addTreeViewerListener(this);
	    setInputForTable(Router.getInstance().getRuleManager());
	}

	private void addTableMenu() {
		MenuManager menuManager = new MenuManager();
		menuManager.add(removeRuleAction);
		menuManager.add(editRuleAction);
		menuManager.add(removeAllExchangesAction);
		menuManager.add(renameRuleAction);
		menuManager.add(showRuleDetailsAction);
		
		final Menu menu = menuManager.createContextMenu(tableViewer.getControl());
		tableViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuManager, tableViewer);
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
	
	@Override
	public void setFocus() {
		tableViewer.getTable().setFocus();
	}

	
	public void setInputForTable(RuleManager manager) {
		if (manager.getTotalNumberOfRules() > 0) {
			enableActions(true);
		} else {
			enableActions(false);
		}
		tableViewer.setInput(manager);
	}

	public void addExchange(Rule rule, Exchange exchange) {
		
	}

	public void addRule(Rule rule) {
		enableActions(true);
		refreshTable();
	}

	public void refresh() {
		
	}

	public void removeExchange(Exchange exchange) {
		refreshTable();
	}

	public void removeExchanges(Rule parent, Exchange[] exchanges) {
		refreshTable();
	}

	public void removeRule(Rule rule, int rulesLeft) {
		if (rulesLeft == 0){
			enableActions(false);
		}
		refreshTable();
	}

	public void selectTo(Object obj) {
		
	}

	public void setExchangeFinished(Exchange exchange) {
		refreshTable();
	}
	
	private void enableActions(boolean enabled) {
		editRuleAction.setEnabled(enabled);
		removeRuleAction.setEnabled(enabled);
		removeAllExchangesAction.setEnabled(enabled);
		renameRuleAction.setEnabled(enabled);
		showRuleDetailsAction.setEnabled(enabled);
	}
	
	private void refreshTable() {
		if (tableViewer.getTable() == null || tableViewer.getTable().isDisposed())
			return;
		tableViewer.getTable().getDisplay().asyncExec(new Runnable() {
			public void run() {
				tableViewer.setInput(Router.getInstance().getRuleManager());
			}
		});
	}

	public void removeExchanges(Exchange[] exchanges) {
		refreshTable();
	}

}
