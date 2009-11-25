package com.predic8.plugin.membrane.views;


import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.providers.RuleStatisticsContentProvider;
import com.predic8.plugin.membrane.providers.RuleStatisticsLabelProvider;
import com.predic8.plugin.membrane.providers.TableHeaderLabelProvider;

public class RuleStatisticsView extends ViewPart implements IRuleTreeViewerListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleStatisticsView";

	private TableViewer tableViewer;

	public RuleStatisticsView() {
		
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		//scomposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
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
		tableViewer.setContentProvider(new RuleStatisticsContentProvider());
		tableViewer.setLabelProvider(new RuleStatisticsLabelProvider());
		GridData tableGridData = new GridData(GridData.FILL_BOTH);
		tableGridData.grabExcessVerticalSpace = true;
		tableGridData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(tableGridData);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MembraneUIPlugin.PLUGIN_ID + "RuleStatistics");
	
	    Router.getInstance().getExchangeStore().addTreeViewerListener(this);
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
		tableViewer.setInput(manager);
	}

	public void addExchange(Rule rule, Exchange exchange) {
		
	}

	public void addRule(Rule rule) {
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
		refreshTable();
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
				tableViewer.setInput(Router.getInstance().getRuleManager());
			}
		});
	}

}
