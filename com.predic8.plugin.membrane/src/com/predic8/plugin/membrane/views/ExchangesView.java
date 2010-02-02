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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
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
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Configuration;
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

	private Label lbFilterCount;

	private Label lbSortedBy;

	private String filterCountText = "";

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(createTopLayout());

		createTableViewer(composite);

		removeExchangeAction = new RemoveExchangeAction(tableViewer);
		removeExchangeAction.setEnabled(false);

		stopExchangeAction = new ExchangeStopAction(tableViewer);
		stopExchangeAction.setEnabled(false);

		removeAllExchangesAction = new ExchangeVirtualListRemoveAction(tableViewer);
		removeAllExchangesAction.setEnabled(false);

		addMenu();

		Composite compControls = new Composite(composite, SWT.NONE);

		GridLayout layout4Controls = new GridLayout();
		layout4Controls.numColumns = 1;
		compControls.setLayout(layout4Controls);

		Composite compositeFilters = new Composite(compControls, SWT.NONE);

		GridLayout layout4Filters = new GridLayout();
		layout4Filters.numColumns = 2;
		compositeFilters.setLayout(layout4Filters);

		createLink(compositeFilters, "<A>Filters</A>", ShowFiltersDialogAction.ID);

		GridData gData4Label = new GridData(GridData.FILL_HORIZONTAL);
		gData4Label.grabExcessHorizontalSpace = true;
		gData4Label.widthHint = 500;

		lbFilterCount = new Label(compositeFilters, SWT.NONE);
		lbFilterCount.setText(filterManager.toString() + filterCountText);
		lbFilterCount.setLayoutData(gData4Label);

		Composite compositeSorters = new Composite(compControls, SWT.NONE);
		GridLayout gridLayoutSorters = new GridLayout();
		gridLayoutSorters.numColumns = 2;
		compositeSorters.setLayout(gridLayoutSorters);

		createLink(compositeSorters, "<A>Sorted By: </A>", ShowSortersDialogAction.ID);

		lbSortedBy = new Label(compositeSorters, SWT.NONE);
		lbSortedBy.setText(comparator.toString());
		lbSortedBy.setLayoutData(gData4Label);

		createTrackRequestButton(compControls);

		Router.getInstance().getExchangeStore().addExchangesViewListener(this);
		refreshTable(false);

		contributeToActionBars();

	}

	private void createTrackRequestButton(Composite compControls) {
		btTrackRequests = new Button(compControls, SWT.CHECK);
		btTrackRequests.setText("Track Requests");
		btTrackRequests.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {
				getConfiguration().setTrackExchange(btTrackRequests.getSelection());
			}

		});

		btTrackRequests.setSelection(getConfiguration().getTrackExchange());

		GridData gdata = new GridData();
		gdata.horizontalIndent = 4;

		btTrackRequests.setLayoutData(gdata);
	}

	private void createLink(Composite composite, String linkText, final String actionId) {
		Link link = new Link(composite, SWT.NONE);
		link.setText(linkText);
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				((ActionContributionItem) getViewSite().getActionBars().getToolBarManager().find(actionId)).getAction().run();
			}
		});
	}
	
	private void createTableViewer(Composite composite) {
		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER | SWT.VIRTUAL);
		createColumns(tableViewer);

		tableViewer.setContentProvider(new ExchangesViewLazyContentProvider(tableViewer));
		tableViewer.setUseHashlookup(true);

		tableViewer.setLabelProvider(new ExchangesViewLabelProvider());

		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.grabExcessVerticalSpace = true;
		gData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(gData);
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
				setInputForMessageView(exc, RequestView.VIEW_ID);
				setInputForMessageView(exc, ResponseView.VIEW_ID);
				canShowBody = true;
			}
		});
	}

	private Layout createTopLayout() {
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginTop = 10;
		layout.marginLeft = 2;
		layout.marginBottom = 5;
		layout.marginRight = 2;
		layout.verticalSpacing = 7;
		return layout;
	}

	private void addMenu() {
		MenuManager manager = new MenuManager();
		manager.add(removeExchangeAction);
		manager.add(stopExchangeAction);
		manager.add(removeAllExchangesAction);

		tableViewer.getControl().setMenu(manager.createContextMenu(tableViewer.getControl()));
		getSite().registerContextMenu(manager, tableViewer);
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

	public void refresh() {
		refreshTable(false);
	}

	public void removeExchange(Exchange exchange) {
		refreshTable(false);
	}

	public void removeExchanges(Rule parent, Exchange[] exchanges) {
		refreshTable(true);
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

	public void refreshTable(final boolean clear) {

		Display.getDefault().asyncExec(new Runnable() {

			public void run() {
				if (!lbSortedBy.isDisposed()) {
					lbSortedBy.setText(comparator.toString());

				}

				if (!lbFilterCount.isDisposed()) {
					lbFilterCount.setText((filterManager.toString() + filterCountText));
				}
				
				List<Exchange> exchanges = applyFilter(Router.getInstance().getExchangeStore().getAllExchangesAsList());
				applySorter(exchanges);
				if (exchanges.size() > 0) {
					removeExchangeAction.setEnabled(true);
					removeAllExchangesAction.setEnabled(true);
				} else {
					removeExchangeAction.setEnabled(false);
					removeAllExchangesAction.setEnabled(false);
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

				if (getConfiguration().getTrackExchange()) {
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
	
	private abstract class MoveThread extends Thread {
		protected int width = 0;
		protected TableColumn column;
		
		public MoveThread(int width, TableColumn column) {
			this.width = width;
			this.column = column;
		}
		
		protected abstract void process();
		
		@Override
		public void run() {
			process();
		}
		
		protected void setWidthForColumn(int i) {
			final int index = i;
			column.getDisplay().syncExec(new Runnable() {
				public void run() {
					column.setWidth(index);
				}
			});
		}
	}

	private class ShrinkThread extends MoveThread {
		public ShrinkThread(int width, TableColumn column) {
			super(width, column);
		}
		
		protected void process() {
			column.getDisplay().syncExec(new Runnable() {
				public void run() {
					column.setData("restoredWidth", new Integer(width));
				}
			});

			for (int i = width; i >= 0; i--) {
				setWidthForColumn(i);
			}
		}
	};

	private class ExpandThread extends MoveThread {
		public ExpandThread(int width, TableColumn column) {
			super(width, column);
		}
		protected void process() {
			for (int i = 0; i <= width; i++) {
				setWidthForColumn(i);
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
		stopExchangeAction.setEnabled(exchange.getStatus() == ExchangeState.STARTED);
	}

	public void removeExchanges(Exchange[] exchanges) {
		refreshTable(true);
	}

	private void setInputForMessageView(HttpExchange exc, String viewId) {
		IWorkbenchPage page = getViewSite().getPage();
		AbstractMessageView messageView = (AbstractMessageView) page.findView(viewId);
		if (messageView == null) {
			try {
				page.showView(RequestView.VIEW_ID);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		messageView.setInput(exc);
		messageView.updateUIStatus(canShowBody);
	}

	private Configuration getConfiguration() {
		return Router.getInstance().getConfigurationManager().getConfiguration();
	}

	
	
	public void addRule(Rule rule) {
		// TODO Delete this method
		
	}

	public void removeRule(Rule rule, int rulesLeft) {
		// TODO Delete this method
	}

}
