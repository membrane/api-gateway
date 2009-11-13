package com.predic8.plugin.membrane.views;

import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Core;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.providers.ExchangesViewContentProvider;
import com.predic8.plugin.membrane.providers.ExchangesViewLabelProvider;
import com.predic8.plugin.membrane.viewers.ExchangeViewerSorter;

public class ExchangesView extends ViewPart implements IRuleTreeViewerListener {

	
	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.ExchangesView";
	
	private TableViewer tableViewer;
	
	public ExchangesView() {
		
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 25;
		gridLayout.marginLeft = 15;
		gridLayout.marginBottom = 80;
		gridLayout.marginRight = 35;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);

		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		
		tableViewer.setContentProvider(new ExchangesViewContentProvider());
		tableViewer.setLabelProvider(new ExchangesViewLabelProvider());
		
		GridData tableGridData = new GridData(GridData.FILL_BOTH);
		tableGridData.grabExcessVerticalSpace = true;
		tableGridData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(tableGridData);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MembraneUIPlugin.PLUGIN_ID + "ExchangesOverview");
	
		tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
				if (selection.getFirstElement() instanceof HttpExchange) {
					HttpExchange exc = (HttpExchange)selection.getFirstElement();
					IWorkbenchPage page = getViewSite().getPage();
					try {
						page.showView(ExchangeView.VIEW_ID);
					} catch (Exception ex) {
						ex.printStackTrace();
					}

					ExchangeView exchangeView = (ExchangeView) getSite().getPage().findView(ExchangeView.VIEW_ID);
					exchangeView.setExchange(exc);
				} 
			}
		});
		
		tableViewer.setSorter(new ExchangeViewerSorter());
		
		Core.getExchangeStore().addTreeViewerListener(this);
		refreshTable();
	}

	
	private void createColumns(TableViewer viewer) {
		String[] titles = { "Time", "Rule", "Method", "Path", "Client", "Server", "Content-Type", "Status-Code", "Request Content Length", "Response Content Length", "Duration"};
		int[] bounds = { 100, 80, 90, 90, 80, 80, 80, 90, 140, 140, 70};	
		
		for (int i = 0; i < titles.length; i++) {
			final TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
			column.getColumn().setAlignment(SWT.CENTER);
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);
			column.getColumn().addSelectionListener(new SelectionAdapter() {
			      public void widgetSelected(SelectionEvent event) {
			    	  if (column.getColumn().getText().equals("Time")) {
			    		  ((ExchangeViewerSorter)tableViewer.getSorter()).setSortTarget(ExchangeViewerSorter.SORT_TARGET_TIME);
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
		refreshTable();
	}

	public void removeExchange(Exchange exchange) {
		refreshTable();
	}

	public void removeExchanges(Rule parent, Exchange[] exchanges) {
		refreshTable();
	}

	public void removeRule(Rule rule, int rulesLeft) {
		
	}

	public void selectTo(Object obj) {
		
	}

	public void setExchangeFinished(Exchange exchange) {
		refreshTable();
	}
	
	private void refreshTable() {
		if (tableViewer.getTable() == null || tableViewer.getTable().isDisposed())
			return;
		tableViewer.getTable().getDisplay().asyncExec(new Runnable() {
			public void run() {
				tableViewer.setInput(Core.getExchangeStore().getAllExchanges());
			}
		});
	}
	
}
