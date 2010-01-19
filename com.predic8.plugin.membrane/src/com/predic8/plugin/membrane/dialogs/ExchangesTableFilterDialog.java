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

package com.predic8.plugin.membrane.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.plugin.membrane.dialogs.components.ClientFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.MethodFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.RuleFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.ServerFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.StatusCodeFilterComposite;
import com.predic8.plugin.membrane.filtering.ClientFilter;
import com.predic8.plugin.membrane.filtering.ExchangesFilter;
import com.predic8.plugin.membrane.filtering.MethodFilter;
import com.predic8.plugin.membrane.filtering.RulesFilter;
import com.predic8.plugin.membrane.filtering.ServerFilter;
import com.predic8.plugin.membrane.filtering.StatusCodeFilter;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ExchangesTableFilterDialog extends Dialog {

	private ExchangesView exchangesView;

	private TabFolder tabFolder;

	private MethodFilterComposite methodFilterComposite;
	
	private RuleFilterComposite rulesFilterComposite;
	
	private ServerFilterComposite serverFilterComposite;
	
	private ClientFilterComposite clientFilterComposite;
	
	private StatusCodeFilterComposite statusCodeFilterComposite;
	
	private Button btRemoveFilters;
	
	public ExchangesTableFilterDialog(Shell parentShell, ExchangesView parent) {
		super(parentShell);
		this.exchangesView = parent;

	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Exchange Filters");
		shell.setSize(440, 560);
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		 Composite composite = new Composite(parent, SWT.NONE);
		 
		 GridLayout layout = new GridLayout();
		 composite.setLayout(layout);
 		 
		 btRemoveFilters = new Button(composite, SWT.PUSH);
		 btRemoveFilters.addSelectionListener(new SelectionAdapter() {
			 @Override
			public void widgetSelected(SelectionEvent e) {
				 rulesFilterComposite.showAllRules();
				 methodFilterComposite.showAllMethods();
				 serverFilterComposite.showAllServers();
				 clientFilterComposite.showAllClients();
				 statusCodeFilterComposite.showAllStatusCodes();
			}
		 });
		 btRemoveFilters.setText("Remove  all  filters");
		
		 GridData gData = new GridData(SWT.RIGHT, SWT.FILL, true, true, 1, 1);
		 gData.grabExcessHorizontalSpace = true;
		 btRemoveFilters.setLayoutData(gData);

		 Label label = new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL);
		 GridData labelGridData = new GridData(410, 12);
		 label.setLayoutData(labelGridData);
		 
		return super.createButtonBar(composite);
	}
	
	
	private ExchangesFilter getFilterForClass(Class<? extends ExchangesFilter> clazz) {
		if (exchangesView.getFilterManager().getFilterForClass(clazz) != null) {
			return exchangesView.getFilterManager().getFilterForClass(clazz);
		} else {
			try {
				return clazz.newInstance();
			} catch (Exception e) {
				throw new RuntimeException("Should never happen.");
			} 
		}
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);

		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		container.setLayout(layout);

		
		tabFolder = new TabFolder(container, SWT.NONE);		
		
		methodFilterComposite = new MethodFilterComposite(tabFolder, (MethodFilter)getFilterForClass(MethodFilter.class));
		rulesFilterComposite = new RuleFilterComposite(tabFolder, (RulesFilter)getFilterForClass(RulesFilter.class));
		serverFilterComposite = new ServerFilterComposite(tabFolder, (ServerFilter)getFilterForClass(ServerFilter.class));
		clientFilterComposite = new ClientFilterComposite(tabFolder, (ClientFilter)getFilterForClass(ClientFilter.class));
		statusCodeFilterComposite = new StatusCodeFilterComposite(tabFolder, (StatusCodeFilter)getFilterForClass(StatusCodeFilter.class));
		
		GridData gdTabs = new GridData();
		gdTabs.grabExcessHorizontalSpace = true;
		gdTabs.widthHint = 400;
		tabFolder.setLayoutData(gdTabs);
		
		TabItem tabItemRule = new TabItem(tabFolder, SWT.NONE);
		tabItemRule.setText("Rule");
		tabItemRule.setControl(rulesFilterComposite);

		TabItem tabItemMethod = new TabItem(tabFolder, SWT.NONE);
		tabItemMethod.setText("Method");
		tabItemMethod.setControl(methodFilterComposite);

		TabItem tabItemStatusCode = new TabItem(tabFolder, SWT.NONE);
		tabItemStatusCode.setText("Status Code");
		tabItemStatusCode.setControl(statusCodeFilterComposite);
		
		TabItem tabItemServer = new TabItem(tabFolder, SWT.NONE);
		tabItemServer.setText("Server");
		tabItemServer.setControl(serverFilterComposite);
		
		TabItem tabItemClient = new TabItem(tabFolder, SWT.NONE);
		tabItemClient.setText("Client");
		tabItemClient.setControl(clientFilterComposite);
		
		return container;
	}

	

	@Override
	protected void okPressed() {
		if (btRemoveFilters.getSelection()) {
			exchangesView.getFilterManager().removeAllFilters();
			exchangesView.reloadAll();
			return;
		}
		if (rulesFilterComposite.getRulesFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(RulesFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(rulesFilterComposite.getRulesFilter());
		}
		
		if (methodFilterComposite.getMethodFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(MethodFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(methodFilterComposite.getMethodFilter());
		}
		
		if (serverFilterComposite.getServerFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(ServerFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(serverFilterComposite.getServerFilter());
		}
		
		if (clientFilterComposite.getClientFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(ClientFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(clientFilterComposite.getClientFilter());
		}
		
		if (statusCodeFilterComposite.getStatusCodeFilter().isDeactivated()) {
			exchangesView.getFilterManager().removeFilter(StatusCodeFilter.class);
		} else {
			exchangesView.getFilterManager().addFilter(statusCodeFilterComposite.getStatusCodeFilter());
		}
		
		exchangesView.reloadAll();
		super.okPressed();
	}

}