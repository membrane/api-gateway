package com.predic8.plugin.membrane.views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeComparator;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.model.IExchangesViewListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.actions.ExchangeStopAction;
import com.predic8.plugin.membrane.actions.ExchangeVirtualListRemoveAction;
import com.predic8.plugin.membrane.actions.RemoveExchangeAction;
import com.predic8.plugin.membrane.actions.ShowFiltersDialogAction;
import com.predic8.plugin.membrane.actions.ShowSortersDialogAction;
import com.predic8.plugin.membrane.contentproviders.ExchangesViewLazyContentProvider;
import com.predic8.plugin.membrane.filtering.FilterManager;
import com.predic8.plugin.membrane.labelproviders.ExchangesViewLabelProvider;

public class ExchangesView extends ViewPart implements IExchangesViewListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.ExchangesView";

	private static final String[] TITLES = { "Status-Code", "Time", "Rule", "Method", "Path", "Client", "Server", "Request Content-Type", "Request Content Length", "Response Content Type", "Response Content Length", "Duration" };
	private static final int[] BOUNDS = { 90, 100, 80, 90, 90, 80, 80, 130, 140, 140, 140, 70 };

	private TableViewer tableViewer;

	private boolean canShowBody = true;

	private Button btTrackRequests;

	private FilterManager filterManager = new FilterManager();

	private Action removeExchangeAction;

	private Action stopExchangeAction;

	private Action removeAllExchangesAction;

	private ExchangeComparator comparator = new ExchangeComparator();

	private Link linkFilter;

	private Label lbFilterCount;

	private Label lbSortedBy;

	private Link linkSortBy;

	private String filterCountText = "";

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 2;
		gridLayout.marginBottom = 5;
		gridLayout.marginRight = 2;
		gridLayout.verticalSpacing = 7;
		composite.setLayout(gridLayout);

		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER | SWT.VIRTUAL);
		createColumns(tableViewer);

		tableViewer.setContentProvider(new ExchangesViewLazyContentProvider(tableViewer));
		tableViewer.setUseHashlookup(true);

		tableViewer.setLabelProvider(new ExchangesViewLabelProvider());

		GridData tableGridData = new GridData(GridData.FILL_BOTH);
		tableGridData.grabExcessVerticalSpace = true;
		tableGridData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(tableGridData);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MembraneUIPlugin.PLUGIN_ID + "ExchangesOverview");

		tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {

				IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
				if (selection.isEmpty()) {
					updateRequestResponseViews(null);
				}

				if (selection.getFirstElement() instanceof HttpExchange) {
					HttpExchange exc = (HttpExchange) selection.getFirstElement();
					updateRequestResponseViews(exc);
					enableStopMenu(exc);
				}
			}

			private void updateRequestResponseViews(HttpExchange exc) {
				IWorkbenchPage page = getViewSite().getPage();
				RequestView requestViewPart = (RequestView) page.findView(RequestView.VIEW_ID);
				ResponseView responseViewPart = (ResponseView) page.findView(ResponseView.VIEW_ID);

				if (requestViewPart == null) {
					try {
						page.showView(RequestView.VIEW_ID);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}

				if (responseViewPart == null) {
					try {
						page.showView(ResponseView.VIEW_ID);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}

				requestViewPart.setInput(exc);
				requestViewPart.updateUIStatus(canShowBody);

				responseViewPart.setInput(exc);
				responseViewPart.updateUIStatus(canShowBody);

				canShowBody = true;
			}
		});

		// tableViewer.setSorter(new ExchangesVieweSorter());

		removeExchangeAction = new RemoveExchangeAction(tableViewer);
		removeExchangeAction.setEnabled(false);

		stopExchangeAction = new ExchangeStopAction(tableViewer);
		stopExchangeAction.setEnabled(false);

		removeAllExchangesAction = new ExchangeVirtualListRemoveAction(tableViewer);
		removeAllExchangesAction.setEnabled(false);

		addMenu();

		Composite compControls = new Composite(composite, SWT.NONE);

		GridLayout gridLayoutForControls = new GridLayout();
		gridLayoutForControls.numColumns = 1;
		compControls.setLayout(gridLayoutForControls);

		Composite compositeFilters = new Composite(compControls, SWT.NONE);
		GridLayout gridLayoutFilters = new GridLayout();
		gridLayoutFilters.numColumns = 2;
		compositeFilters.setLayout(gridLayoutFilters);

		linkFilter = new Link(compositeFilters, SWT.NONE);
		linkFilter.setText("<A>Filters</A>");
		linkFilter.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				((ActionContributionItem) getViewSite().getActionBars().getToolBarManager().find(ShowFiltersDialogAction.ID)).getAction().run();
			}
		});

		GridData gridData4Label = new GridData(GridData.FILL_HORIZONTAL);
		gridData4Label.grabExcessHorizontalSpace = true;
		gridData4Label.widthHint = 500;

		lbFilterCount = new Label(compositeFilters, SWT.NONE);
		lbFilterCount.setText(filterManager.toString() + filterCountText);
		lbFilterCount.setLayoutData(gridData4Label);

		Composite compositeSorters = new Composite(compControls, SWT.NONE);
		GridLayout gridLayoutSorters = new GridLayout();
		gridLayoutSorters.numColumns = 2;
		compositeSorters.setLayout(gridLayoutSorters);

		linkSortBy = new Link(compositeSorters, SWT.NONE);
		linkSortBy.setText("<A>Sorted By: </A>");
		linkSortBy.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ActionContributionItem item = (ActionContributionItem) getViewSite().getActionBars().getToolBarManager().find(ShowSortersDialogAction.ID);
				IAction action = item.getAction();
				action.run();
			}
		});

		lbSortedBy = new Label(compositeSorters, SWT.NONE);
		lbSortedBy.setText(comparator.toString());
		lbSortedBy.setLayoutData(gridData4Label);

		btTrackRequests = new Button(compControls, SWT.CHECK);
		btTrackRequests.setText("Track Requests");
		btTrackRequests.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {
				Router.getInstance().getConfigurationManager().getConfiguration().setTrackExchange(btTrackRequests.getSelection());
			}

		});

		btTrackRequests.setSelection(Router.getInstance().getConfigurationManager().getConfiguration().getTrackExchange());

		GridData gdata = new GridData();
		gdata.horizontalIndent = 4;

		btTrackRequests.setLayoutData(gdata);

		Router.getInstance().getExchangeStore().addTreeViewerListener(this);
		refreshTable(false);

		contributeToActionBars();

	}

	private void addMenu() {
		MenuManager menuManager = new MenuManager();
		menuManager.add(removeExchangeAction);
		menuManager.add(stopExchangeAction);
		menuManager.add(removeAllExchangesAction);

		tableViewer.getControl().setMenu(menuManager.createContextMenu(tableViewer.getControl()));
		getSite().registerContextMenu(menuManager, tableViewer);
	}

	private void createColumns(TableViewer viewer) {

		for (int i = 0; i < TITLES.length; i++) {
			TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
			col.getColumn().setAlignment(SWT.LEFT);
			col.getColumn().setText(TITLES[i]);
			col.getColumn().setWidth(BOUNDS[i]);
			col.getColumn().setResizable(true);
			col.getColumn().setMoveable(true);
		}

		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
	}

	@Override
	public void setFocus() {
		tableViewer.getTable().setFocus();
	}

	public void addExchange(Rule rule, final Exchange exchange) {

	}

	public void addRule(Rule rule) {

	}

	public void refresh() {
		refreshTable(false);
	}

	public void removeExchange(Exchange exchange) {
		refreshTable(false);
	}

	public void removeExchanges(Rule parent, Exchange[] exchanges) {
		refreshTable(true);
	}

	public void removeRule(Rule rule, int rulesLeft) {

	}

	public void selectTo(Object obj) {

	}

	public void setExchangeFinished(Exchange exchange) {
		refreshTable(false);
	}

	private List<Exchange> applyFilter(List<Exchange> exchanges) {
		if (exchanges == null || exchanges.size() == 0) {
			filterCountText = "exchanges list is empty.";
			return new ArrayList<Exchange>();
		}
		if (filterManager.isEmpty()) {
			filterCountText = exchanges.size() + " of " + exchanges.size() + " Exchanges are displayed.";
			return exchanges;
		}

		List<Exchange> filteredExchanges = new ArrayList<Exchange>();
		synchronized (exchanges) {
			for (Exchange exc : exchanges) {
				if (filterManager.filter(exc)) {
					filteredExchanges.add(exc);
				}
			}
		}

		filterCountText = filteredExchanges.size() + " of " + exchanges.size() + " Exchanges are displayed.";
		return filteredExchanges;
	}

	private void applySorter(List<Exchange> exchanges) {
		if (comparator.isEmpty())
			return;
		synchronized (exchanges) {
			if (exchanges == null || exchanges.size() == 0)
				return;
			Collections.sort(exchanges, comparator);
		}
	}

	public void reloadAll() {
		refreshTable(true);
	}

	private void refreshTable(final boolean clear) {

		Display.getDefault().asyncExec(new Runnable() {

			public void run() {
				List<Exchange> exchanges = applyFilter(Router.getInstance().getExchangeStore().getAllExchangesAsList());
				applySorter(exchanges);
				if (exchanges.size() > 0) {
					removeExchangeAction.setEnabled(true);
					removeAllExchangesAction.setEnabled(true);
				} else {
					removeExchangeAction.setEnabled(false);
					removeAllExchangesAction.setEnabled(false);
				}

				if (!lbSortedBy.isDisposed()) {
					lbSortedBy.setText(comparator.toString());

				}

				if (!lbFilterCount.isDisposed()) {
					lbFilterCount.setText((filterManager.toString() + filterCountText));
				}

				if (tableViewer.getTable().isDisposed())
					return;

				if (clear) {
					tableViewer.getTable().removeAll();
					tableViewer.getTable().clearAll();
				}
				Object[] excArray = exchanges.toArray();
				if (tableViewer.getContentProvider() != null) {
					tableViewer.setInput(excArray);
					tableViewer.setItemCount(excArray.length);
				}

				if (Router.getInstance().getConfigurationManager().getConfiguration().getTrackExchange()) {
					canShowBody = false;
					if (excArray.length > 0)
						tableViewer.setSelection(new StructuredSelection(excArray[excArray.length - 1]), true);
				}
				tableViewer.refresh();
				tableViewer.getTable().redraw();
				tableViewer.getTable().layout();

			}
		});
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(new Separator());
		manager.add(new ShowFiltersDialogAction(this));
		manager.add(new ShowSortersDialogAction(this));
	}

	private void fillLocalPullDown(IMenuManager manager) {

		Action action;

		for (int i = 0; i < tableViewer.getTable().getColumnCount(); i++) {
			final TableColumn col = tableViewer.getTable().getColumn(i);

			action = new Action(tableViewer.getTable().getColumn(i).getText(), SWT.CHECK) {
				public void runWithEvent(Event event) {
					if (!isChecked()) {
						new ShrinkThread(col.getWidth(), col).run();
					} else {
						new ExpandThread(((Integer) col.getData("restoredWidth")).intValue(), col).run();
					}
				}

			};
			action.setChecked(true);
			manager.add(action);
		}

		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private class ShrinkThread extends Thread {
		private int width = 0;
		private TableColumn column;

		public ShrinkThread(int width, TableColumn column) {
			this.width = width;
			this.column = column;
		}

		public void run() {
			column.getDisplay().syncExec(new Runnable() {

				public void run() {
					column.setData("restoredWidth", new Integer(width));
				}
			});

			for (int i = width; i >= 0; i--) {
				final int index = i;
				column.getDisplay().syncExec(new Runnable() {

					public void run() {
						column.setWidth(index);
					}

				});
			}
		}
	};

	private class ExpandThread extends Thread {
		private int width = 0;
		private TableColumn column;

		public ExpandThread(int width, TableColumn column) {
			this.width = width;
			this.column = column;
		}

		public void run() {
			for (int i = 0; i <= width; i++) {
				final int index = i;
				column.getDisplay().syncExec(new Runnable() {

					public void run() {
						column.setWidth(index);
					}

				});
			}
		}
	}

	public FilterManager getFilterManager() {
		return filterManager;
	}

	public ExchangeComparator getComparator() {
		return comparator;
	}

	public void setComperator(ExchangeComparator comparator) {
		this.comparator = comparator;
		refreshTable(true);
	}

	private void enableStopMenu(Exchange exchange) {
		if (exchange.getStatus() == ExchangeState.STARTED)
			stopExchangeAction.setEnabled(true);
		else
			stopExchangeAction.setEnabled(false);
	}

	public void removeExchanges(Exchange[] exchanges) {
		refreshTable(true);
	}

}
