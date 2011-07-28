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
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.contentproviders.RuleTableContentProvider;
import com.predic8.plugin.membrane.labelproviders.RuleTableLabelProvider;

public class RuleTableView extends TableViewPart implements IRuleChangeListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleTableView";

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = createComposite(parent);
		
		Composite dummyComposite = new Composite(composite, SWT.NONE);
		dummyComposite.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		new Label(dummyComposite, SWT.NONE).setText(" ");
		
		createTitleLabel(dummyComposite);
		
		createTableViewer(composite);
		Router.getInstance().getRuleManager().addRuleChangeListener(this);
	}

	@Override
	protected IBaseLabelProvider createLabelProvider() {
		return new RuleTableLabelProvider();
	}
	
	@Override
	protected IContentProvider createContentProvider() {
		return new RuleTableContentProvider();
	}
	
	private void createTitleLabel(Composite dummyComposite) {
		Label titleLabel = new Label(dummyComposite, SWT.NONE);
		titleLabel.setText("List of currently available Rules");
		titleLabel.setFont(JFaceResources.getFontRegistry().get(JFaceResources.HEADER_FONT));
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
	protected String[] getTableColumnTitles() {
		return new String[] { "Host", "Listen Port", "Method", "Path", "Target Host", "Target Port" };
	}
	
	@Override
	protected int[] getTableColumnBounds() {
		return new int[] { 140, 80, 60, 120, 160, 80 };
	}
	
	@Override
	public void ruleAdded(Rule rule) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	@Override
	public void ruleRemoved(Rule rule, int rulesLeft) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	@Override
	public void ruleUpdated(Rule rule) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	@Override
	public void rulePositionsChanged() {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}

	@Override
	public void batchUpdate(int size) {
		tableViewer.setInput(Router.getInstance().getRuleManager());
	}
}
