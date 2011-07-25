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

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.contentproviders.RuleTableContentProvider;
import com.predic8.plugin.membrane.labelproviders.RuleTableLabelProvider;

public class RuleTableView extends ViewPart implements IRuleChangeListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleTableView";

	private TableViewer tableViewer;

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = createComposite(parent);
		
		Composite dummyComposite = new Composite(composite, SWT.NONE);
		dummyComposite.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		new Label(dummyComposite, SWT.NONE).setText(" ");
		
		createTitleLabel(dummyComposite);
		
		tableViewer = createTableViewer(composite);

		Router.getInstance().getRuleManager().addRuleChangeListener(this);
	}

	private void createTitleLabel(Composite dummyComposite) {
		Label titleLabel = new Label(dummyComposite, SWT.NONE);
		titleLabel.setText("List of currently available Rules");
		titleLabel.setFont(JFaceResources.getFontRegistry().get(JFaceResources.HEADER_FONT));
	}

	private TableViewer createTableViewer(Composite composite) {
		TableViewer tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		tableViewer.setLabelProvider(new RuleTableLabelProvider());
		tableViewer.setContentProvider(new RuleTableContentProvider());
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.grabExcessVerticalSpace = true;
		gData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(gData);
		return tableViewer;
	}

	private Composite createComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginTop = 25;
		layout.marginLeft = 15;
		layout.marginBottom = 80;
		layout.marginRight = 35;
		layout.verticalSpacing = 20;
		composite.setLayout(layout);
		return composite;
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
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
	}

	public TableViewer getTableViewer() {
		return tableViewer;
	}

	public void ruleAdded(Rule rule) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	public void ruleRemoved(Rule rule) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	public void ruleUpdated(Rule rule) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	public void rulePositionsChanged() {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	public void batchUpdate(int size) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}
}
