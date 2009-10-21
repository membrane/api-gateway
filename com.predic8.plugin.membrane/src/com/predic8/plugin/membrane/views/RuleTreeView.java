package com.predic8.plugin.membrane.views;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Core;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.actions.ExchangeStopAction;
import com.predic8.plugin.membrane.actions.RemoveAllExchangesAction;
import com.predic8.plugin.membrane.actions.RemoveTreeElementAction;
import com.predic8.plugin.membrane.actions.RuleEditAction;
import com.predic8.plugin.membrane.actions.RuleRenameAction;
import com.predic8.plugin.membrane.celleditors.RuleTreeCellEditorModifier;
import com.predic8.plugin.membrane.providers.RuleTreeContentProvider;
import com.predic8.plugin.membrane.providers.RuleTreeLabelProvider;

public class RuleTreeView extends ViewPart {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleTreeView";

	private TreeViewer ruleTreeViewer;

	// private DrillDownAdapter drilDownAdapter;

	private RuleTreeCellEditorModifier ruleTreeCellEditorModifier;

	private RemoveTreeElementAction removeTreeElementAction;

	private RemoveAllExchangesAction removeAllExchangesAction;

	private ExchangeStopAction exchangeStopAction;

	private RuleEditAction ruleEditAction;

	private RuleRenameAction ruleRenameAction;

	public RuleTreeView() {

	}

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 2;
		gridLayout.marginBottom = 10;
		gridLayout.marginRight = 10;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);

		ruleTreeViewer = new TreeViewer(composite);
		ruleTreeViewer.setContentProvider(new RuleTreeContentProvider(ruleTreeViewer));
		ruleTreeViewer.setLabelProvider(new RuleTreeLabelProvider());

		ruleTreeViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent e) {
				if (e.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection selection = (IStructuredSelection) e.getSelection();

					Object selectedItem = selection.getFirstElement();

					if (selectedItem instanceof Exchange) {

						return;
					}
					if (selectedItem instanceof Rule) {
						if (ruleTreeViewer.getExpandedState(selectedItem))
							ruleTreeViewer.collapseToLevel(selectedItem, 1);
						else
							ruleTreeViewer.expandToLevel(selectedItem, 1);
						return;
					}
				}
			}
		});

		final CellEditor[] cellEditors = new CellEditor[1];
		cellEditors[0] = new TextCellEditor(ruleTreeViewer.getTree(), SWT.BORDER);
		ruleTreeViewer.setCellEditors(cellEditors);
		ruleTreeViewer.setColumnProperties(new String[] {"name"});

		ruleTreeCellEditorModifier = new RuleTreeCellEditorModifier();
		ruleTreeCellEditorModifier.setTreeViewer(ruleTreeViewer);
		ruleTreeViewer.setCellModifier(ruleTreeCellEditorModifier);

		TreeViewerEditor.create(ruleTreeViewer, new ColumnViewerEditorActivationStrategy(ruleTreeViewer) {
			protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
				return event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC;
			}
		}, ColumnViewerEditor.DEFAULT);

		// drilDownAdapter = new DrillDownAdapter(ruleTreeViewer);
		ruleTreeViewer.setInput(Core.getRuleManager());

		ruleTreeViewer.addSelectionChangedListener(new RuleTreeSelectionChangeListener());

		GridData treeGridData = new GridData(GridData.FILL_BOTH);
		treeGridData.grabExcessVerticalSpace = true;
		treeGridData.grabExcessHorizontalSpace = true;
		ruleTreeViewer.getTree().setLayoutData(treeGridData);

		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
	}

	@Override
	public void setFocus() {
		ruleTreeViewer.getControl().setFocus();
	}

	private void makeActions() {
		removeTreeElementAction = new RemoveTreeElementAction(ruleTreeViewer);
		removeAllExchangesAction = new RemoveAllExchangesAction(ruleTreeViewer);
		exchangeStopAction = new ExchangeStopAction(ruleTreeViewer);
		ruleEditAction = new RuleEditAction(ruleTreeViewer);
		ruleRenameAction = new RuleRenameAction(ruleTreeViewer);
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				RuleTreeView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(ruleTreeViewer.getControl());
		ruleTreeViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, ruleTreeViewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(removeTreeElementAction);
		manager.add(new Separator());
		manager.add(removeAllExchangesAction);
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(removeTreeElementAction);
		manager.add(removeAllExchangesAction);
		manager.add(ruleEditAction);
		manager.add(ruleRenameAction);
		manager.add(exchangeStopAction);
		manager.add(new Separator());

		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));

	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(new Separator());
		// drilDownAdapter.addNavigationActions(manager);
	}

	private void hookDoubleClickAction() {
		ruleTreeViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {

			}
		});
	}

	class RuleTreeSelectionChangeListener implements ISelectionChangedListener {

		private Object lastSelectedItem;

		public RuleTreeSelectionChangeListener() {
			super();
		}

		private boolean cancel() {

			return false;
		}

		private void enableStopMenu(Exchange exchange) {
			if (exchange.getStatus() == ExchangeState.STARTED)
				exchangeStopAction.setEnabled(true);
			else
				exchangeStopAction.setEnabled(false);
		}

		public void selectionChanged(SelectionChangedEvent e) {
			if (e.getSelection().isEmpty()) {
				RuleDetailsView ruledetailsView = (RuleDetailsView) getSite().getPage().findView(RuleDetailsView.VIEW_ID);
				if (ruledetailsView != null) {
					ruledetailsView.setRuleToDisplay(null);
				}
				return;
			}

			if (!(e.getSelection() instanceof IStructuredSelection)) {
				return;
			}
			IStructuredSelection selection = (IStructuredSelection) e.getSelection();

			Object selectedItem = selection.getFirstElement();
			if (selectedItem == null) {
				removeTreeElementAction.setEnabled(false);
				removeAllExchangesAction.setEnabled(false);
				return;

			}

			if (lastSelectedItem == selectedItem) {
				if (selectedItem instanceof Rule) {
					Rule selectedRule = (Rule) selectedItem;
					int length = Core.getExchangeStore().getExchanges(selectedRule.getRuleKey()).length;
					if (length > 0) {
						removeAllExchangesAction.setEnabled(true);
					} else {
						removeAllExchangesAction.setEnabled(false);
					}
				}
				return;
			}

			removeTreeElementAction.setEnabled(true);

			if (cancel()) {
				ruleTreeViewer.setSelection(new StructuredSelection(lastSelectedItem), true);
				return;
			}

			if (selectedItem instanceof Exchange) {
				Exchange exchange = (Exchange) selectedItem;
				enableStopMenu(exchange);
				IWorkbenchPage page = getViewSite().getPage();
				try {
					page.showView(ExchangeView.VIEW_ID);
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				ExchangeView exchangeView = (ExchangeView) getSite().getPage().findView(ExchangeView.VIEW_ID);
				exchangeView.setExchange(exchange);

				ruleEditAction.setEnabled(false);
				ruleRenameAction.setEnabled(false);
				removeAllExchangesAction.setEnabled(false);
			} else if (selectedItem instanceof Rule) {
				exchangeStopAction.setEnabled(false);
				ruleEditAction.setEnabled(true);
				ruleRenameAction.setEnabled(true);
				int length = Core.getExchangeStore().getNumberOfExchanges(((Rule) selectedItem).getRuleKey());
				if (length > 0) {
					removeAllExchangesAction.setEnabled(true);
				} else {
					removeAllExchangesAction.setEnabled(false);
				}

				IWorkbenchPage page = getViewSite().getPage();
				try {
					page.showView(RuleDetailsView.VIEW_ID);
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				RuleDetailsView ruleView = (RuleDetailsView) getSite().getPage().findView(RuleDetailsView.VIEW_ID);
				ruleView.setRuleToDisplay((Rule) selectedItem);

			}
			lastSelectedItem = selectedItem;
		}

	}

	public RuleTreeCellEditorModifier getRuleTreeCellEditorModifier() {
		return ruleTreeCellEditorModifier;
	}

}
