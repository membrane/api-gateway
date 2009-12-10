package com.predic8.plugin.membrane.views;


import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
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
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.actions.ShowFiltersDialogAction;
import com.predic8.plugin.membrane.filtering.ExchangesViewMethodFilter;
import com.predic8.plugin.membrane.filtering.ExchangesViewStatusCodeFilter;
import com.predic8.plugin.membrane.filtering.FilterManager;
import com.predic8.plugin.membrane.providers.ExchangesViewContentProvider;
import com.predic8.plugin.membrane.providers.ExchangesViewLabelProvider;
import com.predic8.plugin.membrane.providers.ExchangesViewLazyContentProvider;
import com.predic8.plugin.membrane.sorting.ExchangesVieweSorter;

public class ExchangesView extends ViewPart implements IRuleTreeViewerListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.ExchangesView";

	private TableViewer tableViewer;

	private boolean canShowBody = true;

	private Button btTrackRequests;

	private Text textShowLast;
	

	private FilterManager filterManager = new FilterManager();
	
	public ExchangesView() {

	}

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 25;
		gridLayout.marginLeft = 2;
		gridLayout.marginBottom = 30;
		gridLayout.marginRight = 2;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);

		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER| SWT.VIRTUAL);
		createColumns(tableViewer);

		//tableViewer.setContentProvider(new ExchangesViewContentProvider());
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
				}
			}

			private void updateRequestResponseViews(HttpExchange exc) {
				IWorkbenchPage page = getViewSite().getPage();
				RequestView requestViewPart = (RequestView)page.findView(RequestView.VIEW_ID);
				ResponseView responseViewPart = (ResponseView)page.findView(ResponseView.VIEW_ID);
				
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

		tableViewer.setSorter(new ExchangesVieweSorter());

		//addMenu(tableViewer);

		
		Composite compControls = new Composite(composite, SWT.NONE);
		
		GridLayout gridLayoutForControls = new GridLayout();
		gridLayoutForControls.numColumns = 5;
		compControls.setLayout(gridLayoutForControls);
		
		btTrackRequests = new Button(compControls, SWT.CHECK);
		btTrackRequests.setText("Track Requests");
		btTrackRequests.addSelectionListener(new SelectionAdapter() {
			
			public void widgetSelected(SelectionEvent e) {
				Router.getInstance().getConfigurationManager().getConfiguration().setTrackExchange(btTrackRequests.getSelection());
			}
			
		});
		btTrackRequests.setSelection(Router.getInstance().getConfigurationManager().getConfiguration().getTrackExchange());
		
		
		Label lbPad = new Label(compControls, SWT.NONE);
		lbPad.setText("   ");
		GridData gridData4Pad = new GridData(GridData.FILL_BOTH);
		gridData4Pad.widthHint = 300;
		lbPad.setLayoutData(gridData4Pad);
		
		Label lbShowLast = new Label(compControls, SWT.NONE);
		lbShowLast.setText("Show last  ");
		
		textShowLast = new Text(compControls, SWT.BORDER);
		GridData gdShowLast = new GridData(GridData.FILL_BOTH);
		gdShowLast.widthHint = 40;
		textShowLast.setLayoutData(gdShowLast);
		textShowLast.addListener (SWT.Verify, new Listener () {
			public void handleEvent (Event e) {
				String string = e.text;
				char [] chars = new char [string.length ()];
				string.getChars (0, chars.length, chars, 0);
				for (int i=0; i<chars.length; i++) {
					if (!('0' <= chars [i] && chars [i] <= '9')) {
						e.doit = false;
						return;
					}
				}
				
			}
		});
		
		
		textShowLast.addListener (SWT.DefaultSelection, new Listener () {
			public void handleEvent (Event e) {
				String string = textShowLast.getText();
				if (string.length() > 0) {
					try {
						int max = Integer.parseInt(string);
						((ExchangesViewContentProvider)tableViewer.getContentProvider()).setMaxExcahngeCount(max);
						refresh();
					} catch (NumberFormatException nfe) {
						nfe.printStackTrace();
					}
				} else {
					((ExchangesViewContentProvider)tableViewer.getContentProvider()).setMaxExcahngeCount(Integer.MAX_VALUE);
					refresh();
				}
			}
		});
		
		
		Label lbExchanges = new Label(compControls, SWT.NONE);
		lbExchanges.setText(" Exchanges");
		
		Router.getInstance().getExchangeStore().addTreeViewerListener(this);
		refreshTable(false);
		
		contributeToActionBars();
	
	}

	private void createColumns(TableViewer viewer) {
		String[] titles = { "Status-Code", "Time", "Rule", "Method", "Path", "Client", "Server", "Request Content-Type", "Request Content Length", "Response Content Length", "Duration" };
		int[] bounds = {90, 100, 80, 90, 90, 80, 80, 130, 140, 140, 70 };

		for (int i = 0; i < titles.length; i++) {
			final TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
			column.getColumn().setAlignment(SWT.LEFT);
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);

			column.getColumn().addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent event) {
					
					if (column.getColumn().getText().equals("Time")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_TIME);
					} else if (column.getColumn().getText().equals("Rule")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_RULE);
					} else if (column.getColumn().getText().equals("Method")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_METHOD);
					} else if (column.getColumn().getText().equals("Path")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_PATH);
					} else if (column.getColumn().getText().equals("Client")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_CLIENT);
					} else if (column.getColumn().getText().equals("Server")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_SERVER);
					} else if (column.getColumn().getText().equals("Content-Type")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_CONTENT_TYPE);
					} else if (column.getColumn().getText().equals("Status-Code")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_STATUS_CODE);
					} else if (column.getColumn().getText().equals("Request Content Length")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_REQUEST_CONTENT_LENGTH);
					} else if (column.getColumn().getText().equals("Response Content Length")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_RESPONSE_CONTENT_LENGTH);
					} else if (column.getColumn().getText().equals("Duration")) {
						((ExchangesVieweSorter) tableViewer.getSorter()).setSortTarget(ExchangesVieweSorter.SORT_TARGET_DURATION);
					}

					column.getViewer().refresh();
				}
			});

		}

		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
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

	public void removeExchange(final Exchange exchange) {
		refreshTable(false);
//		if (exchange == null || tableViewer.getTable() == null || tableViewer.getTable().isDisposed())
//			return;
//		tableViewer.getTable().getDisplay().asyncExec(new Runnable() {
//			public void run() {
//				tableViewer.remove(exchange);
//			}
//		});
	}

	public void removeExchanges(Rule parent, Exchange[] exchanges) {
		refreshTable(false);
	}

	public void removeRule(Rule rule, int rulesLeft) {

	}

	public void selectTo(Object obj) {

	}

	public void setExchangeFinished(final Exchange exchange) {
		refreshTable(false);
//		if (exchange == null || tableViewer.getTable() == null || tableViewer.getTable().isDisposed())
//			return;
//		
//		tableViewer.getTable().getDisplay().asyncExec(new Runnable() {
//			public void run() {
//				tableViewer.add(exchange);
//			}
//		});
		
	}

	private Object[] applyFilter(Object[] objects) {
		if (objects == null || objects.length == 0)
			return new Object[0];
		
		if (filterManager.isEmpty())
			return objects;
			
		List<Object> filteredList = new ArrayList<Object>();
		for (Object object : objects) {
			if (filterManager.filter((Exchange)object)) {
				filteredList.add(object);
			}
		}	
		return filteredList.toArray();
	}
	
	public void reloadAll() {
		refreshTable(true);
	}
	
	
	private void refreshTable(final boolean clear) {
		if (tableViewer.getTable() == null || tableViewer.getTable().isDisposed())
			return;
		tableViewer.getTable().getDisplay().asyncExec(new Runnable() {
			public void run() {
				if (clear) {
					tableViewer.getTable().removeAll();
					tableViewer.getTable().clearAll();
				}
				
				Object[] array = applyFilter(Router.getInstance().getExchangeStore().getAllExchanges());
				tableViewer.setInput(array);
				tableViewer.setItemCount(array.length);
				if (Router.getInstance().getConfigurationManager().getConfiguration().getTrackExchange()) {
					canShowBody = false;
					tableViewer.setSelection(new StructuredSelection(array[array.length - 1]), true);
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
	}
	
	private void fillLocalPullDown(IMenuManager manager) {
		
		Action action;
		Action actionGet, actionPost, actionDelete, actionHead, actionPut;
		Action action1xx, action2xx, action3xx, action4xx, action5xx;

		for (int i = 0; i < tableViewer.getTable().getColumnCount(); i++) {
			final TableColumn column = tableViewer.getTable().getColumn(i);

			action = new Action(tableViewer.getTable().getColumn(i).getText(), SWT.CHECK) {
				public void runWithEvent(Event event) {
					if (!isChecked()) {
						ShrinkThread t = new ShrinkThread(column.getWidth(), column);
						t.run();
					} else {
						ExpandThread t = new ExpandThread(((Integer) column.getData("restoredWidth")).intValue(), column);
						t.run();
					}
				}

			};

			action.setChecked(true);
			manager.add(action);

		}

		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));

		MenuManager filters = new MenuManager("Filters");

		final MenuManager methodFilters = new MenuManager("Method");

		actionGet = new Action("show only GET", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeMethodFilterFromTableViewer();
				} else {
					IContributionItem[] items = methodFilters.getItems();
					deselectOtherActions(items, "Method Action Show GET");
					updateMethodFilterForTableViewer(Request.METHOD_GET);
				}
			}
		};
		actionGet.setId("Method Action Show GET");

		actionGet.setChecked(false);
		methodFilters.add(actionGet);

		actionPost = new Action("show only POST", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeMethodFilterFromTableViewer();
				} else {
					IContributionItem[] items = methodFilters.getItems();
					deselectOtherActions(items, "Method Action Show POST");
					updateMethodFilterForTableViewer(Request.METHOD_POST);
				}
			}
		};
		actionPost.setId("Method Action Show POST");
		actionPost.setChecked(false);
		methodFilters.add(actionPost);

		actionDelete = new Action("show only DELETE", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeMethodFilterFromTableViewer();
				} else {
					IContributionItem[] items = methodFilters.getItems();
					deselectOtherActions(items, "Method Action Show DELETE");
					updateMethodFilterForTableViewer(Request.METHOD_DELETE);
				}
			}
		};
		actionDelete.setId("Method Action Show DELETE");
		actionDelete.setChecked(false);
		methodFilters.add(actionDelete);

		actionHead = new Action("show only HEAD", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeMethodFilterFromTableViewer();
				} else {
					IContributionItem[] items = methodFilters.getItems();
					deselectOtherActions(items, "Method Action Show HEAD");
					updateMethodFilterForTableViewer(Request.METHOD_HEAD);
				}
			}
		};
		actionHead.setId("Method Action Show HEAD");
		actionHead.setChecked(false);
		methodFilters.add(actionHead);

		actionPut = new Action("show only PUT", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeMethodFilterFromTableViewer();
				} else {
					IContributionItem[] items = methodFilters.getItems();
					deselectOtherActions(items, "Method Action Show PUT");
					updateMethodFilterForTableViewer(Request.METHOD_PUT);
				}
			}
		};
		actionPut.setId("Method Action Show PUT");
		actionPut.setChecked(false);
		methodFilters.add(actionPut);

		filters.add(methodFilters);

		final MenuManager statusCodeFilters = new MenuManager("Status Code");

		action1xx = new Action("1xx", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeStatusCodeFilterFromTableViewer();
				} else {
					IContributionItem[] items = statusCodeFilters.getItems();
					deselectOtherActions(items, "Method Action 1XX");
					updateStatusCodeFilterForTableViewer(100);

				}
			}
		};
		action1xx.setId("Method Action 1XX");
		action1xx.setChecked(false);
		statusCodeFilters.add(action1xx);

		action2xx = new Action("2xx", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeStatusCodeFilterFromTableViewer();
				} else {
					IContributionItem[] items = statusCodeFilters.getItems();
					deselectOtherActions(items, "Method Action 2XX");
					updateStatusCodeFilterForTableViewer(200);
				}
			}
		};
		action2xx.setId("Method Action 2XX");
		action2xx.setChecked(false);
		statusCodeFilters.add(action2xx);

		action3xx = new Action("3xx", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeStatusCodeFilterFromTableViewer();
				} else {
					IContributionItem[] items = statusCodeFilters.getItems();
					deselectOtherActions(items, "Method Action 3XX");
					updateStatusCodeFilterForTableViewer(300);
				}
			}
		};
		action3xx.setId("Method Action 3XX");
		action3xx.setChecked(false);
		statusCodeFilters.add(action3xx);

		action4xx = new Action("4xx", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeStatusCodeFilterFromTableViewer();
				} else {
					IContributionItem[] items = statusCodeFilters.getItems();
					deselectOtherActions(items, "Method Action 4XX");
					updateStatusCodeFilterForTableViewer(400);
				}
			}
		};
		action4xx.setId("Method Action 4XX");
		action4xx.setChecked(false);
		statusCodeFilters.add(action4xx);

		action5xx = new Action("5xx", SWT.CHECK) {
			public void runWithEvent(Event event) {
				if (!isChecked()) {
					removeStatusCodeFilterFromTableViewer();
				} else {
					IContributionItem[] items = statusCodeFilters.getItems();
					deselectOtherActions(items, "Method Action 5XX");
					updateStatusCodeFilterForTableViewer(500);
				}
			}
		};
		action5xx.setId("Method Action 5XX");
		action5xx.setChecked(false);
		statusCodeFilters.add(action5xx);

		filters.add(statusCodeFilters);

		manager.add(filters);
		
	}
	
	private void removeMethodFilterFromTableViewer() {
		ViewerFilter[] filters = tableViewer.getFilters();
		if (filters != null && filters.length > 0) {
			for (ViewerFilter viewerFilter : filters) {
				if (viewerFilter instanceof ExchangesViewMethodFilter) {
					tableViewer.removeFilter(viewerFilter);
					break;
				}
			}
		}
	}

	private void removeStatusCodeFilterFromTableViewer() {
		ViewerFilter[] filters = tableViewer.getFilters();
		if (filters != null && filters.length > 0) {
			for (ViewerFilter viewerFilter : filters) {
				if (viewerFilter instanceof ExchangesViewStatusCodeFilter) {
					tableViewer.removeFilter(viewerFilter);
					break;
				}
			}
		}
	}

	private void deselectOtherActions(IContributionItem[] items, String ownId) {
		for (IContributionItem iContributionItem : items) {
			ActionContributionItem actionItem = (ActionContributionItem) iContributionItem;
			if (!actionItem.getAction().getId().equals(ownId))
				actionItem.getAction().setChecked(false);

		}
	}

	private void updateMethodFilterForTableViewer(String method) {
		ViewerFilter[] filters = tableViewer.getFilters();
		if (filters != null && filters.length > 0) {
			boolean found = false;
			for (ViewerFilter viewerFilter : filters) {
				if (viewerFilter instanceof ExchangesViewMethodFilter) {
					ExchangesViewMethodFilter filter = (ExchangesViewMethodFilter) viewerFilter;
					filter.setRequestMethod(method);
					tableViewer.refresh();
					found = true;
					break;
				}
			}
			if (!found) {
				ExchangesViewMethodFilter filter = new ExchangesViewMethodFilter();
				filter.setRequestMethod(method);
				tableViewer.addFilter(filter);
				tableViewer.refresh();
			}
		} else {
			ExchangesViewMethodFilter filter = new ExchangesViewMethodFilter();
			filter.setRequestMethod(method);
			tableViewer.addFilter(filter);
			tableViewer.refresh();
		}
	}

	private void updateStatusCodeFilterForTableViewer(int statusCode) {
		ViewerFilter[] filters = tableViewer.getFilters();
		if (filters != null && filters.length > 0) {
			boolean found = false;
			for (ViewerFilter viewerFilter : filters) {
				if (viewerFilter instanceof ExchangesViewStatusCodeFilter) {
					ExchangesViewStatusCodeFilter filter = (ExchangesViewStatusCodeFilter) viewerFilter;
					filter.setStatusCode(statusCode);
					tableViewer.refresh();
					found = true;
					break;
				}
			}
			if (!found) {
				ExchangesViewStatusCodeFilter filter = new ExchangesViewStatusCodeFilter();
				filter.setStatusCode(statusCode);
				tableViewer.addFilter(filter);
				tableViewer.refresh();
			}
		} else {
			ExchangesViewStatusCodeFilter filter = new ExchangesViewStatusCodeFilter();
			filter.setStatusCode(statusCode);
			tableViewer.addFilter(filter);
			tableViewer.refresh();
		}
	}

	private class ShrinkThread extends Thread {
		private int width = 0;
		private TableColumn column;

		public ShrinkThread(int width, TableColumn column) {
			super();
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
			super();
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

}
