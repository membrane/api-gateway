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


import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.contentproviders.RuleStatisticsContentProvider;
import com.predic8.plugin.membrane.labelproviders.RuleStatisticsLabelProvider;

public class RuleStatisticsView extends AbstractRulesView {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.RuleStatisticsView";

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = createComposite(parent);

		createRefreshButton(composite);
		
		createTableViewer(composite);
	
		addCellEditorsAndModifiersToViewer();
		
		new Label(composite, SWT.NONE).setText(" All times in ms");
					
	    getExchangeStore().addExchangesViewListener(this);
	    setInputForTable(Router.getInstance().getRuleManager());
	}

	@Override
	protected void addListenersForTableViewer() {
		super.addListenersForTableViewer();
		tableViewer.addDoubleClickListener(createDoubleClickListener());
	}
	
	private IDoubleClickListener createDoubleClickListener() {
		return new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				Object selectedItem = selection.getFirstElement();
				if (selectedItem instanceof Rule) {
					tableViewer.editElement(selectedItem, 0);
				} 
			}
		};
	}
	
	@Override
	protected IBaseLabelProvider createLabelProvider() {
		return new RuleStatisticsLabelProvider();
	}
	
	@Override
	protected IContentProvider createContentProvider() {
		return new RuleStatisticsContentProvider();
	}
	
	private void createRefreshButton(Composite composite) {
		Button btRefresh = new Button(composite, SWT.PUSH);
		btRefresh.setText("Refresh Table");
		btRefresh.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				 setInputForTable(Router.getInstance().getRuleManager());
			}
		});
	}


	private ExchangeStore getExchangeStore() {
		return Router.getInstance().getExchangeStore();
	}

	@Override
	protected void setPropertiesForTableViewer() {
		super.setPropertiesForTableViewer();
		tableViewer.setColumnProperties(new String[] {"name"});
	}

	private Composite createComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 5;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 5;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);
		return composite;
	}

	@Override
	protected String[] getTableColumnTitles() {
		return new String[] { "Rule", "Exchanges", "Minimum Time", "Maximum Time", "Average Time", "Bytes Sent", "Bytes Received", "Errors"};
	}

	@Override
	protected int[] getTableColumnBounds() {
		return new int[] { 140, 80, 90, 90, 100, 80, 90, 70};
	}
	
	@Override
	public void ruleRemoved(Rule rule, int rulesleft) {
		setInputForTable(Router.getInstance().getRuleManager());
	}

	@Override
	public void ruleUpdated(Rule rule) {
		setInputForTable(Router.getInstance().getRuleManager());
	}


	@Override
	public void rulePositionsChanged() {
		setInputForTable(Router.getInstance().getRuleManager());
	}

	@Override
	public void setExchangeStopped(AbstractExchange exchange) {
		// ignore
	}


}
