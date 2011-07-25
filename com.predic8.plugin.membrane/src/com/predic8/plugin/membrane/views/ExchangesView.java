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
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
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

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeComparator;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.PluginUtil;
import com.predic8.plugin.membrane.actions.ShowFiltersDialogAction;
import com.predic8.plugin.membrane.actions.ShowSortersDialogAction;
import com.predic8.plugin.membrane.actions.exchanges.ExchangeStopAction;
import com.predic8.plugin.membrane.actions.exchanges.ExchangeVirtualListRemoveAction;
import com.predic8.plugin.membrane.actions.exchanges.RemoveExchangeAction;
import com.predic8.plugin.membrane.contentproviders.ExchangesViewLazyContentProvider;
import com.predic8.plugin.membrane.filtering.FilterManager;
import com.predic8.plugin.membrane.labelproviders.ExchangesViewLabelProvider;
import com.predic8.plugin.membrane.views.util.ExpandThread;
import com.predic8.plugin.membrane.views.util.ShrinkThread;

public class ExchangesView extends TableViewPart implements IExchangesStoreListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.ExchangesView";

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
	protected String[] getTableColumnTitles() {
		return new String[] {"Status-Code", "Time", "Rule", "Method", "Path", "Client", "Server", "Request Content-Type", "Request Content Length", "Response Content Type", "Response Content Length", "Duration" };
	}
	
	@Override
	protected int[] getTableColumnBounds() {
		return new int[] { 90, 100, 80, 90, 90, 80, 80, 130, 140, 140, 140, 70 };
	}
	
	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(createTopLayout());

		createTableViewer(composite);
		extendTableViewer();
		
		createActions();

		addMenu();

		Composite filters = createInnerComposite(composite, 2);

		createLink(filters, "<A>Filters</A>", ShowFiltersDialogAction.ID);

		GridData gData = createLabelGridData();

		createLabelFilterCount(filters, gData);

		Composite sorters = createInnerComposite(composite, 2);

		createLink(sorters, "<A>Sorted By: </A>", ShowSortersDialogAction.ID);

		createLabelSortedBy(gData, sorters);

		Composite controls = createInnerComposite(composite, 1);
		createTrackRequestButton(controls);

		Router.getInstance().getExchangeStore().addExchangesViewListener(this);
		refreshTable(false);

		contributeToActionBars();

	}

	private void createLabelSortedBy(GridData gData, Composite composite) {
		lbSortedBy = new Label(composite, SWT.NONE);
		lbSortedBy.setText(comparator.toString());
		lbSortedBy.setLayoutData(gData);
	}

	private void createLabelFilterCount(Composite composite, GridData gData) {
		lbFilterCount = new Label(composite, SWT.NONE);
		lbFilterCount.setText(filterManager.toString() + filterCountText);
		lbFilterCount.setLayoutData(gData);
	}

	private void createActions() {
		removeExchangeAction = new RemoveExchangeAction(tableViewer);
		removeExchangeAction.setEnabled(false);

		stopExchangeAction = new ExchangeStopAction(tableViewer);
		stopExchangeAction.setEnabled(false);

		removeAllExchangesAction = new ExchangeVirtualListRemoveAction(tableViewer);
		removeAllExchangesAction.setEnabled(false);
	}

	private GridData createLabelGridData() {
		GridData gData = new GridData(GridData.FILL_HORIZONTAL);
		gData.grabExcessHorizontalSpace = true;
		gData.widthHint = 500;
		return gData;
	}

	private Composite createInnerComposite(Composite parent, int columns) {
		Composite composite = new Composite(parent, SWT.NONE);

		GridLayout layout = new GridLayout();
		layout.numColumns = columns;
		composite.setLayout(layout);
		return composite;
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
	
	@Override
	protected IBaseLabelProvider createLabelProvider() {
		return new ExchangesViewLabelProvider();
	}
	
	@Override
	protected IContentProvider createContentProvider() {
		return new ExchangesViewLazyContentProvider(tableViewer);
	}
	
	private void extendTableViewer() {
				
		tableViewer.setUseHashlookup(true);

		tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {

				IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
				if (selection.isEmpty()) {
					updateRequestResponseViews(null);
				}

				if (selection.getFirstElement() instanceof Exchange) {
					Exchange exc = (Exchange) selection.getFirstElement();
					updateRequestResponseViews(exc);
					enableStopMenu(exc);
				}
			}

			private void updateRequestResponseViews(Exchange exc) {
				setInputForMessageView(exc, ResponseView.VIEW_ID);
				setInputForMessageView(exc, RequestView.VIEW_ID);
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

	@Override
	public void setFocus() {
		tableViewer.getTable().setFocus();
	}

	public void addExchange(Rule rule, final AbstractExchange exchange) {
		
	}

	public void refresh() {
		refreshTable(false);
	}

	public void removeExchange(AbstractExchange exchange) {
		refreshTable(false);
	}

	public void removeExchanges(Rule parent, AbstractExchange[] exchanges) {
		refreshTable(true);
	}

	public void setExchangeFinished(AbstractExchange exchange) {
		refreshTable(false);
	}

	private List<AbstractExchange> applyFilter(List<AbstractExchange> exchanges) {
		if (exchanges == null || exchanges.size() == 0) {
			filterCountText = "exchanges list is empty.";
			return new ArrayList<AbstractExchange>();
		}
		if (filterManager.isEmpty()) {
			filterCountText = exchanges.size() + " of " + exchanges.size() + " Exchanges are displayed.";
			return exchanges;
		}

		List<AbstractExchange> filteredExchanges = new ArrayList<AbstractExchange>();
		synchronized (exchanges) {
			for (AbstractExchange exc : exchanges) {
				if (filterManager.filter(exc)) {
					filteredExchanges.add(exc);
				}
			}
		}

		filterCountText = filteredExchanges.size() + " of " + exchanges.size() + " Exchanges are displayed.";
		return filteredExchanges;
	}

	private void applySorter(List<AbstractExchange> exchanges) {
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

				List<AbstractExchange> exchanges = applyFilter(Router.getInstance().getExchangeStore().getAllExchangesAsList());
				applySorter(exchanges);
				
				if (!lbFilterCount.isDisposed()) {
					lbFilterCount.setText((filterManager.toString() + filterCountText));
				}
				
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
		manager.add(removeAllExchangesAction);
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

	private void enableStopMenu(AbstractExchange exchange) {
		stopExchangeAction.setEnabled(exchange.getStatus() == ExchangeState.STARTED);
	}

	public void removeExchanges(AbstractExchange[] exchanges) {
		refreshTable(true);
	}

	private void setInputForMessageView(Exchange exc, String viewId) {
		AbstractMessageView part = (AbstractMessageView) PluginUtil.showView(viewId);
		part.setInput(exc);
		part.updateUIStatus(canShowBody);
	}

	private Configuration getConfiguration() {
		return Router.getInstance().getConfigurationManager().getConfiguration();
	}

	
	
	public void ruleAdded(Rule rule) {
		// TODO Delete this method
		
	}

	public void removeRule(Rule rule, int rulesLeft) {
		// TODO Delete this method
	}

	public void setExchangeStopped(AbstractExchange exchange) {
		if (exchange == null)
			return;
		
		if (exchange.getRule().isBlockRequest() || exchange.getRule().isBlockResponse()) {
			refreshTable(false);
		}
	}

}
