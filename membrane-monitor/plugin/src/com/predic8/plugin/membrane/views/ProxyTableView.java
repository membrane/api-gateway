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
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.model.IRuleChangeListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.contentproviders.ProxyTableContentProvider;
import com.predic8.plugin.membrane.labelproviders.ProxyTableLabelProvider;
import com.predic8.plugin.membrane.util.SWTUtil;

public class ProxyTableView extends TableViewPart implements IRuleChangeListener {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.ProxyTableView";

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = createComposite(parent);
		
		Composite titleComposite = new Composite(composite, SWT.NONE);
		titleComposite.setLayout(new RowLayout(SWT.HORIZONTAL));
		
		new Label(titleComposite, SWT.NONE).setText(" ");
		
		createTitleLabel(titleComposite);
		
		createTableViewer(composite);
		Router.getInstance().getRuleManager().addRuleChangeListener(this);
	}

	@Override
	protected IBaseLabelProvider createLabelProvider() {
		return new ProxyTableLabelProvider();
	}
	
	@Override
	protected IContentProvider createContentProvider() {
		return new ProxyTableContentProvider();
	}
	
	private void createTitleLabel(Composite composite) {
		Label lb = new Label(composite, SWT.NONE);
		lb.setText("List of currently available Proxis");
		lb.setFont(JFaceResources.getFontRegistry().get(JFaceResources.HEADER_FONT));
	}

	private Composite createComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = SWTUtil.createGridLayout(1, 25, 15, 80, 35);
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
		reloadTableViewer();
	}

	@Override
	public void ruleRemoved(Rule rule, int rulesLeft) {
		reloadTableViewer();
	}

	@Override
	public void ruleUpdated(Rule rule) {
		reloadTableViewer();
	}

	@Override
	public void rulePositionsChanged() {
		reloadTableViewer();
	}

	@Override
	public void batchUpdate(int size) {
		reloadTableViewer();
	}
	
	private void reloadTableViewer() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				tableViewer.setInput(Router.getInstance().getRuleManager());
			}
		});
	}
}
