package com.predic8.plugin.membrane.views;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.contentproviders.RuleTableContentProvider;
import com.predic8.plugin.membrane.labelproviders.RuleTableLabelProvider;

public class RuleTableView extends ViewPart implements IRuleChangeListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleTableView";

	private TableViewer tableViewer;

	public RuleTableView() {
		
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
		
		Composite dummyComposite = new Composite(composite, SWT.NONE);
		dummyComposite.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		Label dummyLabel = new Label(dummyComposite, SWT.NONE); 
		dummyLabel.setText(" ");
		
		Label titleLabel = new Label(dummyComposite, SWT.NONE);
		titleLabel.setText("List of currently available Rules");
		titleLabel.setFont(JFaceResources.getFontRegistry().get(JFaceResources.HEADER_FONT));
		
		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		tableViewer.setLabelProvider(new RuleTableLabelProvider());
		tableViewer.setContentProvider(new RuleTableContentProvider());
		GridData tableGridData = new GridData(GridData.FILL_BOTH);
		tableGridData.grabExcessVerticalSpace = true;
		tableGridData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(tableGridData);

		Router.getInstance().getRuleManager().addTableViewerListener(this);
	}

	@Override
	public void setFocus() {
		tableViewer.getTable().setFocus();
	}

	private void createColumns(TableViewer viewer) {
		String[] titles = { "Host", "Listen Port", "Method", "Path", "Target Host", "Target Port" };
		int[] bounds = { 140, 80, 60, 120, 160, 80 };

		for (int i = 0; i < titles.length; i++) {
			TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);
		}
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
	}

	public TableViewer getTableViewer() {
		return tableViewer;
	}

	public void addRule(Rule rule) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	public void removeRule(Rule rule) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	public void updateRule(Rule rule) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}
}
